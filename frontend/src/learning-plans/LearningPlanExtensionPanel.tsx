import { Check, Send, X } from 'lucide-react';
import { useState } from 'react';
import { formatDifficulty, formatProblemTitle, formatTopicTag } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import type { LearningPlanExtensionReadyEvent, LearningPlanPhaseDraft } from '../types/api';

interface LearningPlanExtensionPanelProps {
  loading: boolean;
  extension?: LearningPlanExtensionReadyEvent;
  onGenerate: (instruction: string) => Promise<boolean>;
  onRevise: (proposalGroupId: number, instruction: string) => Promise<boolean>;
  onApply: (proposalGroupId: number) => Promise<void>;
  onDiscard: (proposalGroupId: number) => Promise<void>;
}

function ExtensionPhaseBlock({ phase }: { phase: LearningPlanPhaseDraft }) {
  const { locale, resources } = useI18n();

  return (
    <section className="phase-block" key={phase.phaseIndex}>
      <div className="phase-heading">
        <h3>{phase.title}</h3>
        <span>{resources.common.week(phase.durationWeeks)}</span>
      </div>
      <p>{phase.focus}</p>
      <div className="tag-row">
        {phase.recommendedTags.map((tag) => (
          <span className="tag-pill" key={tag}>{formatTopicTag(tag, resources)}</span>
        ))}
      </div>
      <div className="problem-list compact-problems">
        {phase.problems.map((problem) => (
          <div className="problem-row" key={problem.slug}>
            <span className="problem-id">{problem.frontendId ?? '-'}</span>
            <span className="problem-title">
              <strong>{formatProblemTitle(problem, locale)}</strong>
              <small>{problem.reason}</small>
            </span>
            <span className={`difficulty-badge ${String(problem.difficulty ?? '').toLowerCase()}`}>
              {formatDifficulty(problem.difficulty, resources)}
            </span>
          </div>
        ))}
      </div>
    </section>
  );
}

export default function LearningPlanExtensionPanel({
  loading,
  extension,
  onGenerate,
  onRevise,
  onApply,
  onDiscard,
}: LearningPlanExtensionPanelProps) {
  const { resources } = useI18n();
  const [instruction, setInstruction] = useState('');
  const [revisionInstruction, setRevisionInstruction] = useState('');
  const generateInstructionId = 'learning-plan-extension-instruction';
  const revisionInstructionId = extension
    ? `learning-plan-extension-${extension.proposalGroupId}-revision`
    : 'learning-plan-extension-revision';

  return (
    <section className="learning-plan-extension-panel">
      <label className="topic-field" htmlFor={generateInstructionId}>
        <span>{resources.learningPlans.extensionEntryLabel}</span>
        <textarea
          disabled={loading}
          id={generateInstructionId}
          onChange={(event) => setInstruction(event.target.value)}
          rows={3}
          value={instruction}
        />
      </label>
      <button
        className="primary-button"
        disabled={loading || !instruction.trim()}
        onClick={() => {
          void onGenerate(instruction.trim())
            .then((generated) => {
              if (generated) {
                setInstruction('');
              }
            })
            .catch(() => undefined);
        }}
        type="button"
      >
        <Send aria-hidden="true" />
        <span>{resources.learningPlans.generateExtension}</span>
      </button>

      {extension ? (
        <div className="pending-extension-block">
          <div className="panel-title">
            <h2>{resources.learningPlans.pendingExtensionTitle}</h2>
          </div>
          {extension.extensionDraft.summary && (
            <div className="goal-summary">
              <p>{extension.extensionDraft.summary}</p>
            </div>
          )}
          <div className="plan-preview">
            {extension.extensionDraft.newPhases.map((phase) => (
              <ExtensionPhaseBlock key={phase.phaseIndex} phase={phase} />
            ))}
          </div>
          <div className="draft-revision-panel extension-revision-panel">
            <label className="topic-field" htmlFor={revisionInstructionId}>
              <span>{resources.learningPlans.reviseExtensionLabel}</span>
              <textarea
                disabled={loading}
                id={revisionInstructionId}
                onChange={(event) => setRevisionInstruction(event.target.value)}
                rows={3}
                value={revisionInstruction}
              />
            </label>
            <div className="draft-action-row extension-action-row">
              <button
                className="secondary-button"
                disabled={loading || !revisionInstruction.trim()}
                onClick={() => {
                  void onRevise(extension.proposalGroupId, revisionInstruction.trim())
                    .then((revised) => {
                      if (revised) {
                        setRevisionInstruction('');
                      }
                    })
                    .catch(() => undefined);
                }}
                type="button"
              >
                <Send aria-hidden="true" />
                <span>{resources.learningPlans.reviseExtension}</span>
              </button>
              <button
                className="primary-button"
                disabled={loading}
                onClick={() => {
                  void onApply(extension.proposalGroupId).catch(() => undefined);
                }}
                type="button"
              >
                <Check aria-hidden="true" />
                <span>{resources.learningPlans.applyExtension}</span>
              </button>
              <button
                className="secondary-button"
                disabled={loading}
                onClick={() => {
                  void onDiscard(extension.proposalGroupId).catch(() => undefined);
                }}
                type="button"
              >
                <X aria-hidden="true" />
                <span>{resources.learningPlans.discardExtension}</span>
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}
