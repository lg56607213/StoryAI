package com.storyai.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관리자 접근 제어. 로그인한 사용자의 이메일이 ADMIN_EMAILS(콤마 구분)에 있으면 관리자.
 * 기본값은 사장님 구글 계정. 다른 관리자를 추가하려면 환경변수 ADMIN_EMAILS 설정.
 */
@Component
public class AdminGuard {

    private final Set<String> adminEmails;

    public AdminGuard(@Value("${ADMIN_EMAILS:lg56607213@gmail.com}") String csv) {
        this.adminEmails = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toLowerCase())
                .collect(Collectors.toSet());
    }

    public boolean isAdmin(Authentication auth) {
        String email = LoginIdentity.emailOf(auth);
        return email != null && adminEmails.contains(email.toLowerCase());
    }

    /** 관리자가 아니면 403. */
    public void require(Authentication auth) {
        if (!isAdmin(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
    }
}
