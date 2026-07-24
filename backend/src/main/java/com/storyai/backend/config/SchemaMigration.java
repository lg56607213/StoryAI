package com.storyai.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MySQL enum 컬럼을 VARCHAR로 교정한다.
 *
 * Hibernate는 @Enumerated(STRING) 속성을 MySQL에서 enum('A','B') 컬럼으로 생성하는데,
 * ddl-auto=update 는 기존 컬럼 정의를 바꾸지 않는다. 그래서 코드에 enum 값을 추가하면
 * ("OLDER_BROTHER" 등) 저장 시 "Data truncated for column ..." 오류가 발생한다.
 *
 * 부팅 시 해당 컬럼이 아직 enum 타입이면 VARCHAR로 한 번만 바꿔 준다(이미 varchar면 건너뜀).
 * 앞으로 enum 값을 추가해도 스키마 오류가 나지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaMigration {

    /** (테이블, 컬럼) — @Enumerated(STRING)으로 저장되는 컬럼들. */
    private static final List<String[]> ENUM_COLUMNS = List.of(
            new String[]{"story_character", "role"},
            new String[]{"video_job", "output_type"},
            new String[]{"video_job", "story_theme"},
            new String[]{"video_job", "age_group"},
            new String[]{"video_job", "book_style"},
            new String[]{"video_job", "video_style"},
            new String[]{"video_job", "book_phase"},
            new String[]{"video_job", "status"},
            new String[]{"video_job", "current_step"}
    );

    private static final int TARGET_LENGTH = 40;

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    public void migrateEnumColumns() {
        int changed = 0;
        for (String[] tc : ENUM_COLUMNS) {
            try {
                if (convertIfEnum(tc[0], tc[1])) {
                    changed++;
                }
            } catch (Exception e) {
                // 개별 실패가 부팅을 막지 않도록(예: H2 개발 환경, 테이블 미존재)
                log.debug("스키마 점검 건너뜀 {}.{}: {}", tc[0], tc[1], e.getMessage());
            }
        }
        if (changed > 0) {
            log.info("스키마 교정 완료: enum 컬럼 {}개를 VARCHAR({})로 변경", changed, TARGET_LENGTH);
        }
    }

    /** 컬럼이 아직 MySQL enum이면 VARCHAR로 변경한다. 변경했으면 true. */
    private boolean convertIfEnum(String table, String column) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                table, column);
        if (rows.isEmpty()) {
            return false; // 테이블·컬럼 없음(신규 DB 등)
        }
        String type = String.valueOf(rows.get(0).get("COLUMN_TYPE"));
        if (type == null || !type.toLowerCase().startsWith("enum")) {
            return false; // 이미 varchar → 할 일 없음
        }
        boolean nullable = "YES".equalsIgnoreCase(String.valueOf(rows.get(0).get("IS_NULLABLE")));
        String ddl = "ALTER TABLE `" + table + "` MODIFY `" + column + "` VARCHAR(" + TARGET_LENGTH + ")"
                + (nullable ? "" : " NOT NULL");
        jdbc.execute(ddl);
        log.info("스키마 교정: {}.{} enum -> VARCHAR({})", table, column, TARGET_LENGTH);
        return true;
    }
}
