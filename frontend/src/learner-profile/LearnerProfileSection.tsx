import { AlertCircle, BrainCircuit } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { learnerProfileAnchor } from '../app/navigation';
import { useI18n } from '../i18n/I18nProvider';
import { getLearnerProfile, requireApiData } from '../services/api';
import type {
  LearnerProfileCitation,
  LearnerProfileDocumentResponse,
} from '../types/api';
import LearnerProfileDocumentRenderer from './LearnerProfileDocumentRenderer';
import LearnerProfileEvidenceDrawer from './LearnerProfileEvidenceDrawer';
import { formatLearnerProfileDateTime } from './profilePresentation';

export default function LearnerProfileSection({
  onProfileAnchorHandled,
  profileAnchor,
}: {
  onProfileAnchorHandled?: () => void;
  profileAnchor?: string;
} = {}) {
  const { locale, resources } = useI18n();
  const [document, setDocument] = useState<LearnerProfileDocumentResponse>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedCitation, setSelectedCitation] = useState<LearnerProfileCitation>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const handledAnchorRef = useRef<string | undefined>(undefined);

  const loadDocument = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError('');
    try {
      const response = await getLearnerProfile(signal);
      const profile = requireApiData(response, resources.myPage.memoryLoadFailed);
      if (!signal?.aborted) {
        setDocument(profile);
      }
    } catch (requestError) {
      if (!signal?.aborted) {
        setError(resources.myPage.memoryLoadFailed);
      }
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }, [resources.myPage.memoryLoadFailed]);

  useEffect(() => {
    const controller = new AbortController();
    void loadDocument(controller.signal);
    return () => controller.abort();
  }, [loadDocument, locale]);

  const normalizedProfileAnchor = learnerProfileAnchor(profileAnchor);

  useEffect(() => {
    if (!normalizedProfileAnchor || loading || handledAnchorRef.current === normalizedProfileAnchor) {
      return undefined;
    }

    let highlightTimer: number | undefined;
    const scrollTimer = window.setTimeout(() => {
      if (handledAnchorRef.current === normalizedProfileAnchor) {
        return;
      }
      handledAnchorRef.current = normalizedProfileAnchor;
      const target = globalThis.document.getElementById(normalizedProfileAnchor);
      if (target) {
        target.scrollIntoView?.({ block: 'center' });
        target.focus({ preventScroll: true });
        target.classList.add('learner-profile-return-highlight');
        highlightTimer = window.setTimeout(() => {
          target.classList.remove('learner-profile-return-highlight');
        }, 2400);
      } else {
        window.scrollTo?.({ top: 0, behavior: 'auto' });
      }
      onProfileAnchorHandled?.();
    });

    return () => {
      window.clearTimeout(scrollTimer);
      if (highlightTimer !== undefined) {
        window.clearTimeout(highlightTimer);
      }
    };
  }, [document?.documentRevision, loading, normalizedProfileAnchor, onProfileAnchorHandled]);

  function openCitation(citation: LearnerProfileCitation) {
    setSelectedCitation(citation);
    setDrawerOpen(true);
  }

  const hasBlocks = Boolean(document && document.blocks.length > 0);
  const title = document?.title ?? resources.myPage.memoryTitle;

  return (
    <section className="learner-memory-section learner-profile-section" aria-labelledby="learner-memory-title">
      <div className="learner-memory-heading">
        <div className="my-card-title">
          <span className="my-card-title-icon learner-memory-title-icon" aria-hidden="true">
            <BrainCircuit />
          </span>
          <div>
            <p className="my-section-eyebrow">{resources.myPage.memoryEyebrow}</p>
            <h2 id="learner-memory-title">{title}</h2>
            <p>{resources.myPage.memorySubtitle}</p>
          </div>
        </div>
        {document?.updatedAt ? (
          <span className="learner-memory-latest">
            {resources.myPage.memoryUpdatedAt(formatLearnerProfileDateTime(document.updatedAt, locale))}
          </span>
        ) : null}
      </div>

      {loading ? <div className="learner-memory-state" role="status">{resources.myPage.memoryLoading}</div> : null}
      {!loading && error ? (
        <div className="learner-memory-state error" role="alert">
          <AlertCircle aria-hidden="true" />
          <span>{error}</span>
          <button className="secondary-button compact" onClick={() => void loadDocument()} type="button">
            {resources.app.retry}
          </button>
        </div>
      ) : null}
      {!loading && !error && !hasBlocks ? (
        <div className="learner-memory-state empty">{resources.myPage.memoryEmpty}</div>
      ) : null}
      {!loading && !error && document && hasBlocks ? (
        <LearnerProfileDocumentRenderer
          document={document}
          openCitation={openCitation}
          selectedCitationNumber={drawerOpen ? selectedCitation?.displayNumber : undefined}
        />
      ) : null}
      <LearnerProfileEvidenceDrawer
        citation={selectedCitation}
        onClose={() => setDrawerOpen(false)}
        open={drawerOpen}
      />
    </section>
  );
}
