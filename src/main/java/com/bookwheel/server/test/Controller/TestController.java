package com.bookwheel.server.test.Controller;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test")
public class TestController {

  @GetMapping("/success")
  public ApiResponse<String> testSucess(){
    return ApiResponse.success("테스트 성공");
  }

  @GetMapping("/businessError")
  public ApiResponse<Void> testBusinessError(){
    throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
  }

  @GetMapping("/unexpectedError")
  public ApiResponse<Void> testUnexpectedError(){
    throw new RuntimeException("런타임 에러");
  }


}
