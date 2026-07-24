package com.storyai.backend.workflow.handler;

import com.storyai.backend.domain.bookpage.BookPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 삽화 한 장을 만들 때마다 해당 페이지의 imageUrl을 "즉시" 독립 트랜잭션으로 커밋한다.
 *
 * 삽화는 여러 스레드에서 병렬 생성되므로, 삽화 단계가 다 끝난 뒤 한꺼번에 저장하면
 * 도중에(예: 20장째) 서버가 죽었을 때 이미 만든 그림의 DB 표시가 남지 않아 재시도 시 다시 만들게 된다.
 * 페이지별로 REQUIRES_NEW 트랜잭션에서 바로 저장하면, 만든 그림은 한 장도 버리지 않고 재사용된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PageImagePersister {

    private final BookPageRepository bookPageRepository;

    /** 생성된 삽화 URL을 해당 페이지에 즉시 반영·커밋한다(스레드마다 독립 트랜잭션). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveImageUrl(Long pageId, String url) {
        bookPageRepository.findById(pageId).ifPresent(p -> {
            p.setImageUrl(url);
            bookPageRepository.save(p);
        });
    }
}
