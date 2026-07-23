package com.storyai.backend.domain.mascot;

import com.storyai.backend.ai.image.ImageGenerator;
import com.storyai.backend.domain.videojob.BookStyle;
import com.storyai.backend.storage.LocalStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "히어로 친구들" 마스코트 캐릭터 시트를 (캐릭터 × 화풍) 조합당 딱 1회 생성해 영구 캐시한다.
 * 이후 모든 주문이 같은 시트를 참조하므로 마스코트가 항상 동일한 모습으로 나오고,
 * 페이지 삽화의 추가 이미지 비용은 0이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MascotSheetService {

    private final ImageGenerator imageGenerator;
    private final LocalStorage storage;

    /** 조합별 생성 동시성 제어(병렬 삽화에서 같은 시트를 중복 생성하지 않도록). */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    /** 마스코트 시트 바이트. 캐시에 있으면 즉시, 없으면 1회 생성 후 캐시. 실패 시 null(동반자 없이 진행). */
    public byte[] sheetFor(Mascot mascot, BookStyle style) {
        if (mascot == null || !imageGenerator.isAvailable()) {
            return null;
        }
        String name = fileName(mascot, style);
        byte[] cached = storage.readShared(name);
        if (cached != null) {
            return cached;
        }
        synchronized (locks.computeIfAbsent(name, k -> new Object())) {
            cached = storage.readShared(name); // 락 획득 사이에 다른 스레드가 만들었을 수 있음
            if (cached != null) {
                return cached;
            }
            try {
                byte[] img = imageGenerator.mascotSheet(
                        mascot.getAppearance(), style != null ? style.getGuide() : null);
                storage.storeShared(name, img);
                log.info("🎨 마스코트 시트 최초 생성: {} ({}) — 이후 모든 책에서 재사용",
                        mascot.getKoreanName(), style != null ? style.getLabel() : "기본");
                return img;
            } catch (Exception e) {
                log.warn("마스코트 시트 생성 실패({}): {}", mascot.getKoreanName(), e.getMessage());
                return null;
            }
        }
    }

    /** 캐시 파일명 규칙. */
    public String fileName(Mascot mascot, BookStyle style) {
        return "mascot-" + mascot.name() + "-" + (style != null ? style.name() : "DEFAULT") + ".png";
    }

    /** 이미 생성돼 있는지(홈페이지 노출 등에서 확인용). */
    public String urlIfExists(Mascot mascot, BookStyle style) {
        String name = fileName(mascot, style);
        return storage.readShared(name) != null ? storage.sharedUrl(name) : null;
    }
}
