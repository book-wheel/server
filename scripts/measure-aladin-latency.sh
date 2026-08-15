#!/usr/bin/env bash
#
# 알라딘 ItemLookUp API 응답 시간 실측 스크립트
#
# RestClientConfig.aladinRestClient 의 타임아웃(connect 2s / read 4s)은 실측이 아니라
# 보수적으로 잡은 값이다. 2026-08-14 read timeout 장애 이후 근거 있는 값으로 조정하기 위해
# 정보나루 클라이언트처럼 실측 데이터를 남기는 것이 목적이다.
#
# 실제 운영 경로의 지연을 재야 하므로 로컬이 아니라 배포 서버(랩 서버)에서 실행할 것.
#
# 사용법:
#   ./scripts/measure-aladin-latency.sh                 # 기본 50회
#   ./scripts/measure-aladin-latency.sh -n 100 -i 0.3   # 100회, 요청 간 0.3초
#   ./scripts/measure-aladin-latency.sh -f isbn.txt     # 측정할 ISBN 목록 지정(줄바꿈 구분)
#   ./scripts/measure-aladin-latency.sh -o result.csv   # 원본 데이터 저장 위치 지정
#
# ISBN 목록을 주지 않으면 알라딘 베스트셀러 API로 실제 ISBN을 받아와 사용한다.
# TTB 키는 ALADIN_API_KEY 환경 변수를 우선 사용하고, 없으면 application-dev.properties 값을 쓴다.
# 알라딘 TTB 키는 일일 호출 한도(5,000회)가 있으므로 -n 값을 과하게 키우지 말 것.

set -euo pipefail

COUNT=50
INTERVAL=0.5
ISBN_FILE=""
OUTPUT="aladin-latency-$(date +%Y%m%d-%H%M%S).csv"

# 현재 설정값. 실측 결과가 이 값을 넘긴 비율을 함께 출력한다.
CONNECT_TIMEOUT=2.0
READ_TIMEOUT=4.0

while getopts "n:i:f:o:h" opt; do
    case "$opt" in
        n) COUNT="$OPTARG" ;;
        i) INTERVAL="$OPTARG" ;;
        f) ISBN_FILE="$OPTARG" ;;
        o) OUTPUT="$OPTARG" ;;
        h) sed -n '2,25p' "$0"; exit 0 ;;
        *) echo "사용법: $0 [-n 횟수] [-i 간격초] [-f ISBN파일] [-o 결과CSV]" >&2; exit 1 ;;
    esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPERTIES="$SCRIPT_DIR/../src/main/resources/application-dev.properties"

TTB_KEY="${ALADIN_API_KEY:-}"
if [ -z "$TTB_KEY" ] && [ -f "$PROPERTIES" ]; then
    TTB_KEY="$(grep '^aladin.api.key=' "$PROPERTIES" | cut -d= -f2-)"
fi
if [ -z "$TTB_KEY" ]; then
    echo "TTB 키를 찾을 수 없다. ALADIN_API_KEY 환경 변수를 설정할 것." >&2
    exit 1
fi

LOOKUP_URL="https://www.aladin.co.kr/ttb/api/ItemLookUp.aspx"
LIST_URL="https://www.aladin.co.kr/ttb/api/ItemList.aspx"

# 같은 ISBN만 반복하면 캐시 효과로 응답 시간이 낙관적으로 나오므로 여러 ISBN을 번갈아 호출한다.
collect_isbns() {
    if [ -n "$ISBN_FILE" ]; then
        grep -oE '[0-9]{13}' "$ISBN_FILE"
        return
    fi

    curl -s --max-time 30 \
        "$LIST_URL?ttbkey=$TTB_KEY&QueryType=Bestseller&MaxResults=50&start=1&SearchTarget=Book&output=js&Version=20131101" \
        | grep -oE '"isbn13":"[0-9Xx]{13}"' \
        | grep -oE '[0-9Xx]{13}'
}

mapfile -t ISBNS < <(collect_isbns | awk 'NF && !seen[$0]++')

if [ "${#ISBNS[@]}" -eq 0 ]; then
    echo "측정에 사용할 ISBN을 확보하지 못했다. -f 로 ISBN 목록을 직접 지정할 것." >&2
    exit 1
fi

echo "대상: $LOOKUP_URL"
echo "ISBN ${#ISBNS[@]}종을 순환하며 ${COUNT}회 호출 (간격 ${INTERVAL}초)"
echo "결과 CSV: $OUTPUT"
echo

echo "seq,isbn,http_code,connect,tls,ttfb,total" > "$OUTPUT"

for ((i = 1; i <= COUNT; i++)); do
    isbn="${ISBNS[$(((i - 1) % ${#ISBNS[@]}))]}"

    # connect: TCP 연결, tls: TLS 핸드셰이크, ttfb: 요청 전송 후 첫 바이트까지(서버 처리 시간),
    # total: 전체. Java 의 connectTimeout 은 connect+tls, readTimeout 은 ttfb 구간에 대응한다.
    read -r connect appconnect pretransfer starttransfer total code <<<"$(
        curl -s -o /dev/null \
            --max-time 30 \
            -w '%{time_connect} %{time_appconnect} %{time_pretransfer} %{time_starttransfer} %{time_total} %{http_code}' \
            "$LOOKUP_URL?ttbkey=$TTB_KEY&itemIdType=ISBN13&ItemId=$isbn&output=js&Version=20131101&Cover=Big&OptResult=itemPage,subInfo" \
            || echo "0 0 0 0 0 000"
    )"

    tls="$(awk -v a="$appconnect" -v c="$connect" 'BEGIN { printf "%.3f", (a > 0 ? a - c : 0) }')"
    ttfb="$(awk -v s="$starttransfer" -v p="$pretransfer" 'BEGIN { printf "%.3f", s - p }')"

    echo "$i,$isbn,$code,$connect,$tls,$ttfb,$total" >> "$OUTPUT"
    printf '%3d/%d  isbn=%s  http=%s  connect=%ss tls=%ss ttfb=%ss total=%ss\n' \
        "$i" "$COUNT" "$isbn" "$code" "$connect" "$tls" "$ttfb" "$total"

    if [ "$i" -lt "$COUNT" ]; then
        sleep "$INTERVAL"
    fi
done

echo
echo "===== 요약 ($OUTPUT) ====="
awk -F, -v connect_timeout="$CONNECT_TIMEOUT" -v read_timeout="$READ_TIMEOUT" '
NR == 1 { next }
{
    n++
    if ($3 != "200") { failed++ }
    connect[n] = $4 + $5   # connect + tls = Java connectTimeout 대응 구간
    ttfb[n] = $6
    total[n] = $7
    if (connect[n] > connect_timeout) { connect_over++ }
    if (ttfb[n] > read_timeout) { read_over++ }
}
function sortarr(arr, cnt,   i, j, key) {
    for (i = 2; i <= cnt; i++) {
        key = arr[i]
        j = i - 1
        while (j >= 1 && arr[j] > key) { arr[j + 1] = arr[j]; j-- }
        arr[j + 1] = key
    }
}
function percentile(arr, cnt, p,   idx) {
    idx = int(cnt * p + 0.9999)
    if (idx < 1) { idx = 1 }
    if (idx > cnt) { idx = cnt }
    return arr[idx]
}
# gawk 의 asort 는 mawk 에 없으므로 직접 정렬한다.
function report(label, arr, cnt) {
    sortarr(arr, cnt)
    printf "%-14s p50 %.3fs   p95 %.3fs   p99 %.3fs   max %.3fs\n", \
        label, percentile(arr, cnt, 0.50), percentile(arr, cnt, 0.95), \
        percentile(arr, cnt, 0.99), arr[cnt]
}
END {
    if (n == 0) { print "측정 데이터 없음"; exit }
    printf "요청 수: %d (비정상 응답 %d건)\n\n", n, failed + 0
    report("connect+tls", connect, n)
    report("ttfb(server)", ttfb, n)
    report("total", total, n)
    printf "\n현재 설정 기준 초과 건수: connect %.1fs 초과 %d건 / read %.1fs 초과 %d건\n", \
        connect_timeout, connect_over + 0, read_timeout, read_over + 0
}
' "$OUTPUT"

echo
echo "타임아웃 조정 시 위 p95/p99/max 와 초과 건수를 RestClientConfig 주석에 근거로 남길 것."
