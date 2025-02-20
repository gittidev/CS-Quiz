package com.quizplatform.core.dto.battle;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

// 답변 제출 요청 DTO
@Getter
@Builder
public class BattleAnswerRequest {
    private UUID roomId;
    private UUID questionId;
    private String answer;
    private int timeSpentSeconds;
}
