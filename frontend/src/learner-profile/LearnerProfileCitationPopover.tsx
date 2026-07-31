import type { LearnerProfileCitation } from '../types/api';
import LearnerProfileEvidenceTimeline from './LearnerProfileEvidenceTimeline';

interface LearnerProfileCitationPopoverProps {
  citation: LearnerProfileCitation;
  id: string;
}

export default function LearnerProfileCitationPopover({ citation, id }: LearnerProfileCitationPopoverProps) {
  return (
    <aside className="learner-profile-citation-popover" id={id} role="tooltip">
      <p>{citation.sourceSummary}</p>
      {citation.previewEvidence.length > 0 ? (
        <LearnerProfileEvidenceTimeline compact items={citation.previewEvidence} />
      ) : null}
    </aside>
  );
}
