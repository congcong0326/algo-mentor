import { useState } from 'react';
import { learnerProfileStatementAnchorId } from '../app/navigation';
import { useI18n } from '../i18n/I18nProvider';
import type { KeyboardEvent } from 'react';
import type { LearnerProfileCitation } from '../types/api';
import LearnerProfileCitationPopover from './LearnerProfileCitationPopover';

interface LearnerProfileSupportedTextProps {
  citation: LearnerProfileCitation;
  drawerOpen: boolean;
  onOpen: (citation: LearnerProfileCitation) => void;
  text: string;
}

export default function LearnerProfileSupportedText({
  citation,
  drawerOpen,
  onOpen,
  text,
}: LearnerProfileSupportedTextProps) {
  const { resources } = useI18n();
  const [previewOpen, setPreviewOpen] = useState(false);
  const anchorId = learnerProfileStatementAnchorId(citation.claimRevisionId);
  const popoverId = `${anchorId}-preview`;

  function openDrawer() {
    setPreviewOpen(false);
    onOpen(citation);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      openDrawer();
    }
  }

  return (
    <span
      aria-label={resources.myPage.openCitation(citation.displayNumber)}
      className="learner-profile-supported-text-wrap"
      id={anchorId}
      role="group"
      tabIndex={-1}
    >
      <button
        aria-controls="learner-profile-evidence-drawer"
        aria-describedby={previewOpen ? popoverId : undefined}
        aria-expanded={drawerOpen}
        aria-label={resources.myPage.openCitation(citation.displayNumber)}
        className="learner-profile-supported-text"
        onBlur={() => setPreviewOpen(false)}
        onClick={openDrawer}
        onFocus={() => setPreviewOpen(true)}
        onKeyDown={handleKeyDown}
        onMouseEnter={() => setPreviewOpen(true)}
        onMouseLeave={() => setPreviewOpen(false)}
        type="button"
      >
        <span>{text}</span>
        <sup aria-hidden="true">[{citation.displayNumber}]</sup>
      </button>
      {previewOpen ? <LearnerProfileCitationPopover citation={citation} id={popoverId} /> : null}
    </span>
  );
}
