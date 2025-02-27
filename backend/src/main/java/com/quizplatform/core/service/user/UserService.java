package com.quizplatform.core.service.user;


import com.quizplatform.core.domain.quiz.Achievement;
import com.quizplatform.core.domain.quiz.QuizAttempt;
import com.quizplatform.core.domain.user.LevelUpRecord;
import com.quizplatform.core.domain.user.User;
import com.quizplatform.core.domain.user.UserLevel;
import com.quizplatform.core.dto.user.*;
import com.quizplatform.core.repository.UserRepository;
import com.quizplatform.core.repository.question.QuestionAttemptRepository;
import com.quizplatform.core.repository.quiz.QuizAttemptRepository;
import com.quizplatform.core.repository.tag.TagRepository;
import com.quizplatform.core.repository.user.AchievementRepository;
import com.quizplatform.core.repository.user.UserLevelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserLevelRepository userLevelRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuestionAttemptRepository questionAttemptRepository;
    private final TagRepository tagRepository;
    private final AchievementRepository achievementRepository;

    @Cacheable(value = "userProfiles", key = "#userId")
    public UserProfileDto getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + userId));

        return new UserProfileDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getProfileImage(),
                user.getLevel(),
                user.getExperience(),
                user.getRequiredExperience(),
                user.getTotalPoints(),
                formatDateTime(user.getCreatedAt()),
                user.getLastLogin() != null ? formatDateTime(user.getLastLogin()) : null
        );
    }

    @Cacheable(value = "userStatistics", key = "#userId")
    public UserStatisticsDto getUserStatistics(Long userId) {
        // 퀴즈 시도 통계를 계산하는 로직
        long totalQuizzesTaken = quizAttemptRepository.countByUserId(userId);
        long totalQuizzesCompleted = quizAttemptRepository.countByUserIdAndIsCompletedTrue(userId);
        Double averageScore = quizAttemptRepository.getAverageScoreByUserId(userId);
        long totalCorrectAnswers = questionAttemptRepository.countCorrectAnswersByUserId(userId);
        long totalQuestions = questionAttemptRepository.countTotalQuestionsByUserId(userId);
        Double correctRate = totalQuestions > 0 ? (double) totalCorrectAnswers / totalQuestions * 100 : 0;
        int totalTimeTaken = quizAttemptRepository.getTotalTimeTakenByUserId(userId);
        Integer bestScore = quizAttemptRepository.getMaxScoreByUserId(userId);
        Integer worstScore = quizAttemptRepository.getMinScoreByUserId(userId);

        return new UserStatisticsDto(
                (int) totalQuizzesTaken,
                (int) totalQuizzesCompleted,
                averageScore != null ? averageScore : 0.0,
                (int) totalCorrectAnswers,
                (int) totalQuestions,
                correctRate,
                totalTimeTaken,
                bestScore != null ? bestScore : 0,
                worstScore != null ? worstScore : 0
        );
    }

    public List<RecentActivityDto> getRecentActivities(Long userId, int limit) {
        // 퀴즈 시도 활동
        List<RecentActivityDto> activities = new ArrayList<>();

        // Pageable 객체 생성 (0은 첫 페이지, limit은 페이지 크기)
        PageRequest pageRequest = PageRequest.of(0, limit);

        // 리포지토리 메서드 호출
        List<QuizAttempt> recentAttempts = quizAttemptRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);



        // 업적 획득 활동
        List<Object[]> achievementRecords = achievementRepository.findRecentAchievementsByUserId(userId, limit);
        for (Object[] record : achievementRecords) {
            Long id = ((Number) record[0]).longValue();
            String achievementStr = (String) record[2];  // enum 문자열
            String achievementName = (String) record[3];
            LocalDateTime earnedAt = (LocalDateTime) record[4];

            // Achievement enum의 ordinal 값을 ID로 사용
            Long achievementId = null;
            try {
                Achievement achievement = Achievement.valueOf(achievementStr);
                achievementId = (long) achievement.ordinal();
            } catch (IllegalArgumentException e) {
                // 잘못된 enum 값이 있을 경우 로깅하고 계속 진행
                achievementId = -1L;  // 기본값 설정
            }

            activities.add(new RecentActivityDto(
                    id,
                    "ACHIEVEMENT_EARNED",
                    null,
                    null,
                    null,
                    achievementId,
                    achievementName,
                    null,
                    formatDateTime(ZonedDateTime.from(earnedAt))
            ));
        }

        // 레벨업 활동 (구현 필요)
        List<LevelUpRecord> recentLevelUps = userLevelRepository.findRecentLevelUpsByUserId(userId, limit);
        for (LevelUpRecord record : recentLevelUps) {
            activities.add(new RecentActivityDto(
                    record.getId(),
                    "LEVEL_UP",
                    null,
                    null,
                    null,
                    null,
                    null,
                    record.getNewLevel(),
                    formatDateTime(record.getOccurredAt())
            ));
        }

        // 최신순 정렬 및 limit 적용
        return activities.stream()
                .sorted(Comparator.comparing(RecentActivityDto::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "userAchievements", key = "#userId")
    public List<AchievementDto> getAchievements(Long userId) {
        UserLevel userLevel = userLevelRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("사용자 레벨 정보를 찾을 수 없습니다: " + userId));

        // 사용자가 획득한 업적 목록
        Set<Achievement> earnedAchievements = userLevel.getAchievements();

        // 모든 업적 정보를 가져와 DTO로 변환
        List<AchievementDto> result = new ArrayList<>();
        for (Achievement achievement : Achievement.values()) {
            boolean isEarned = earnedAchievements.contains(achievement);
            String earnedAt = null;

            if (isEarned) {
                // 업적 획득 시간 조회
                Object[] record = achievementRepository.findByUserIdAndAchievement(userId, achievement.name());
                if (record != null) {
                    LocalDateTime earnedAtTime = (LocalDateTime) record[4]; // 인덱스는 네이티브 쿼리 결과 순서에 맞춰 조정
                    earnedAt = earnedAtTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            }

            int progress = isEarned ? 100 : calculateAchievementProgress(userId, achievement);

            result.add(new AchievementDto(
                    (long) achievement.ordinal(),
                    achievement.getName(),
                    achievement.getDescription(),
                    achievement.getIconUrl(),
                    earnedAt,
                    progress,
                    achievement.getRequirementDescription()
            ));
        }

        return result;
    }

    private int calculateAchievementProgress(Long userId, Achievement achievement) {
        // 업적 종류에 따른 진행도 계산 로직 (구현 필요)
        return 0; // 기본 구현
    }

    @Cacheable(value = "userTopicPerformance", key = "#userId")
    public List<TopicPerformanceDto> getTopicPerformance(Long userId) {
        // 태그별 성과 데이터 조회 (복잡한 조인 쿼리 필요)
        List<Object[]> tagPerformances = tagRepository.getTagPerformanceByUserId(userId);

        List<TopicPerformanceDto> result = new ArrayList<>();
        for (Object[] row : tagPerformances) {
            Long tagId = (Long) row[0];
            String tagName = (String) row[1];
            Long quizzesTaken = (Long) row[2];
            Double averageScore = (Double) row[3];
            Double correctRate = (Double) row[4];

            // 강점 여부 판단 (평균 점수가 75% 이상이면 강점으로 간주)
            boolean isStrength = averageScore >= 75.0;

            result.add(new TopicPerformanceDto(
                    tagId,
                    tagName,
                    quizzesTaken.intValue(),
                    averageScore,
                    correctRate,
                    isStrength
            ));
        }

        return result;
    }

    @Transactional
    @CacheEvict(value = "userProfiles", key = "#userId")
    public UserProfileDto updateProfile(Long userId, UserProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + userId));

        // 프로필 업데이트
        user.updateProfile(request.getUsername(), request.getProfileImage());
        userRepository.save(user);

        return getUserProfile(userId);
    }

    private String formatDateTime(ZonedDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}