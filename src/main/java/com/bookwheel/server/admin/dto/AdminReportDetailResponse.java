package com.bookwheel.server.admin.dto;

import java.util.List;

public record AdminReportDetailResponse(AdminReportResponse report, boolean targetExists,
                                        boolean authorActive, List<String> imageUrls) {}
