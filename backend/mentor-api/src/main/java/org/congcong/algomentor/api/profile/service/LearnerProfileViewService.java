package org.congcong.algomentor.api.profile.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.congcong.algomentor.api.profile.mapper.LearnerProfileMapper;
import org.congcong.algomentor.api.profile.mapper.model.LearnerProfileViewRow;
import org.congcong.algomentor.api.profile.model.LearnerProfileEntryResponse;
import org.congcong.algomentor.api.profile.model.LearnerProfileResponse;
import org.congcong.algomentor.api.profile.model.LearnerProfileTagResponse;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryKind;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LearnerProfileViewService {

  private final Supplier<LearnerProfileMapper> mapperSupplier;

  @Autowired
  public LearnerProfileViewService(ObjectProvider<LearnerProfileMapper> mapperProvider) {
    this(mapperProvider::getIfAvailable);
  }

  LearnerProfileViewService(LearnerProfileMapper mapper) {
    this(() -> mapper);
  }

  private LearnerProfileViewService(Supplier<LearnerProfileMapper> mapperSupplier) {
    this.mapperSupplier = mapperSupplier;
  }

  public LearnerProfileResponse getProfile(long userId) {
    List<LearnerProfileEntryResponse> declaredFacts = new ArrayList<>();
    List<LearnerProfileEntryResponse> generalObservations = new ArrayList<>();
    List<LearnerProfileEntryResponse> tagAssessments = new ArrayList<>();
    Instant latestUpdatedAt = null;

    for (LearnerProfileViewRow row : mapper().findCurrentForDisplay(userId)) {
      LearnerProfileEntryKind kind = LearnerProfileEntryKind.valueOf(row.entryKind());
      LearnerProfileEntryResponse response = toResponse(row);
      switch (kind) {
        case DECLARED_FACT -> declaredFacts.add(response);
        case GENERAL_OBSERVATION -> generalObservations.add(response);
        case TAG_ASSESSMENT -> tagAssessments.add(response);
      }
      if (latestUpdatedAt == null || row.updatedAt().isAfter(latestUpdatedAt)) {
        latestUpdatedAt = row.updatedAt();
      }
    }

    return new LearnerProfileResponse(
        declaredFacts,
        generalObservations,
        tagAssessments,
        latestUpdatedAt);
  }

  private LearnerProfileEntryResponse toResponse(LearnerProfileViewRow row) {
    LearnerProfileTagResponse tag = row.tagId() == null
        ? null
        : new LearnerProfileTagResponse(
            row.tagId(), row.tagValue(), row.tagLabelEn(), row.tagLabelZh());
    return new LearnerProfileEntryResponse(
        row.id(),
        row.dimension(),
        row.revisionNo(),
        row.contentText(),
        row.originType(),
        row.updatedAt(),
        tag);
  }

  private LearnerProfileMapper mapper() {
    LearnerProfileMapper mapper = mapperSupplier.get();
    if (mapper == null) {
      throw new LearnerProfileMapperUnavailableException();
    }
    return mapper;
  }

  public static class LearnerProfileMapperUnavailableException extends RuntimeException {
    public LearnerProfileMapperUnavailableException() {
      super("Learner profile mapper is unavailable. Enable the local datasource profile before using learner profile APIs.");
    }
  }
}
