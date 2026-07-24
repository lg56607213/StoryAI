package com.storyai.backend.workflow.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

/**
 * 서버 전체에서 "동시에 생성 중인 삽화 이미지 수"를 제한하는 전역 게이트.
 *
 * 삽화 1장은 이미지 바이트 + 디코딩 버퍼로 메모리를 꽤 쓰는데, 여러 주문(여러 사용자)이
 * 동시에 생성되면 각 주문마다 병렬 생성이 곱해져 메모리가 한도를 넘고 컨테이너가 OOM으로 죽는다.
 * 이 게이트로 "몇 명이 몰려도 서버 전체에서 동시 N장까지만" 만들고 나머지는 대기시켜,
 * 메모리 초과 자체를 막는다(개인은 조금 기다리지만 서버는 안 죽는다).
 */
@Slf4j
@Component
public class ImageGenerationGate {

    private final Semaphore semaphore;
    private final int permits;

    public ImageGenerationGate(@Value("${storyai.book.global-image-concurrency:2}") int permits) {
        this.permits = Math.max(1, permits);
        this.semaphore = new Semaphore(this.permits, true); // 공정(FIFO) — 먼저 온 주문이 먼저 처리
    }

    /** 허가를 얻을 때까지 대기한 뒤 이미지 생성 작업을 실행하고, 끝나면 허가를 반납한다. */
    public <T> T run(Callable<T> task) throws Exception {
        semaphore.acquire();
        try {
            return task.call();
        } finally {
            semaphore.release();
        }
    }

    public int permits() {
        return permits;
    }
}
