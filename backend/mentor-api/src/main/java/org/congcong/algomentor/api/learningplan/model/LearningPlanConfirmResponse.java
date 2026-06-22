package org.congcong.algomentor.api.learningplan.model;

import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;

public record LearningPlanConfirmResponse(long planId, String title, LearningPlanStatus status) {
}
