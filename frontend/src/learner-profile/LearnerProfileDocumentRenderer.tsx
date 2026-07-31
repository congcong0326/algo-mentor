import type {
  LearnerProfileCitation,
  LearnerProfileDocumentResponse,
  LearnerProfileDocumentSpan,
} from '../types/api';
import LearnerProfileSupportedText from './LearnerProfileSupportedText';

interface LearnerProfileDocumentRendererProps {
  document: LearnerProfileDocumentResponse;
  openCitation: (citation: LearnerProfileCitation) => void;
  selectedCitationNumber?: number;
}

export default function LearnerProfileDocumentRenderer({
  document,
  openCitation,
  selectedCitationNumber,
}: LearnerProfileDocumentRendererProps) {
  function citationFor(displayNumber: number): LearnerProfileCitation | undefined {
    return document.citationMap[String(displayNumber)];
  }

  function renderSpan(span: LearnerProfileDocumentSpan, index: number) {
    switch (span.type) {
      case 'TEXT':
        return <span key={`text-${index}`}>{span.text}</span>;
      case 'SUPPORTED_TEXT': {
        const citation = citationFor(span.citationDisplayNumber);
        if (!citation) {
          return <span className="learner-profile-document-error" key={`missing-${index}`} role="alert">Unsupported citation.</span>;
        }
        return (
          <LearnerProfileSupportedText
            citation={citation}
            drawerOpen={selectedCitationNumber === citation.displayNumber}
            key={`supported-${citation.displayNumber}`}
            onOpen={openCitation}
            text={span.text}
          />
        );
      }
      default:
        return <span className="learner-profile-document-error" key={`unknown-${index}`} role="alert">Unsupported document span.</span>;
    }
  }

  return (
    <article className="learner-profile-document" data-document-revision={document.documentRevision}>
      {document.blocks.map((block, index) => {
        switch (block.type) {
          case 'HEADING':
            return <h3 key={`heading-${index}`}>{block.spans.map(renderSpan)}</h3>;
          case 'PARAGRAPH':
            return <p key={`paragraph-${index}`}>{block.spans.map(renderSpan)}</p>;
          default:
            return <p className="learner-profile-document-error" key={`unknown-block-${index}`} role="alert">Unsupported document block.</p>;
        }
      })}
    </article>
  );
}
