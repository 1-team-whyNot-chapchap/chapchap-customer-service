package com.chapchap.customer.global.error;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.GlobalResponse;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    private ResponseEntity<GlobalResponse<Void>> generateErrorResponse(CustomResponseCode customResponseCode) {
        return ResponseEntity.status(customResponseCode.getHttpStatus())
                   .body(GlobalResponse.<Void>from(customResponseCode));
    }

    /**
     * 업무 규칙 실패와 기능별 BusinessException 하위 예외를 처리한다.
     * 기술/계약 예외는 이 경계에 포함하지 않고 기본 시스템 오류 처리로 전달한다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<GlobalResponse<Void>> handleBusinessException(BusinessException e) {
        log.debug(e.getMessage(), e);
        return this.generateErrorResponse(e.getCustomResponseCode());
    }

    // -----------------------------------
    // 이하 Spring에서 발생하는 Exception
    // -----------------------------------
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GlobalResponse<Void>> handle(AccessDeniedException e) {
        log.debug(CustomResponseCode.UNAUTHORIZED_ERROR.name(), e);
        // 현재 로그인한 사용자의 정보를 컨텍스트에서 확인
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 로그인하지 않은 익명 사용자가 접근한 경우 (인증 실패 - 401)
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return this.generateErrorResponse(CustomResponseCode.UNAUTHENTICATED_ERROR); // E02
        }

        // 로그인은 했으나 권한(Role)이 부족한 경우 (인가 실패 - 403)
        return this.generateErrorResponse(CustomResponseCode.UNAUTHORIZED_ERROR);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<GlobalResponse<Void>> handle(MethodArgumentTypeMismatchException e) {
        log.debug(CustomResponseCode.INVALID_PARAMETER_ERROR.name(), String.format("%s : 필드를 확인해 주세요.", e.getName()));
        return this.generateErrorResponse(CustomResponseCode.INVALID_PARAMETER_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GlobalResponse<Void>> handle(MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult()
             .getFieldErrors()
             .stream()
             .collect(Collectors.toMap(
                 FieldError::getField, // 필드명
                 fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "유효하지 않은 값입니다.",
                 (existing, replacement) -> existing // 중복 필드가 있을 경우 기존 값 유지
             ));

        log.debug(CustomResponseCode.INVALID_PARAMETER_ERROR.name(), errors);
        return this.generateErrorResponse(CustomResponseCode.INVALID_PARAMETER_ERROR);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<GlobalResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.debug(CustomResponseCode.INVALID_PARAMETER_ERROR.name(), e);
        return this.generateErrorResponse(CustomResponseCode.INVALID_PARAMETER_ERROR);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<GlobalResponse<Void>> handle(NoResourceFoundException e) {
        return this.generateErrorResponse(CustomResponseCode.NOT_FOUND_ERROR);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<GlobalResponse<Void>> handle(DuplicateKeyException e) {
        log.error("DB 에러", e);
        return this.generateErrorResponse(CustomResponseCode.DB_DUPLICATED_KEY_ERROR);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<GlobalResponse<Void>> handle(DataAccessException e) {
        log.error("DB 에러", e);
        return this.generateErrorResponse(CustomResponseCode.DB_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalResponse<Void>> handle(Exception e) {
        log.error("시스템 에러", e);
        return this.generateErrorResponse(CustomResponseCode.SYSTEM_ERROR);
    }
}




