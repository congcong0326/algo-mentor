import { hierarchy, pack } from 'd3-hierarchy';
import type { CSSProperties, PointerEvent as ReactPointerEvent } from 'react';
import { useId } from 'react';
import { useI18n } from '../i18n/I18nProvider';
import type { AbilityProfileResponse, AbilityTagScore } from '../types/api';

interface AbilityBubbleChartProps {
  profile: AbilityProfileResponse;
  tags?: AbilityTagScore[];
}

interface BubbleDatum {
  children?: BubbleDatum[];
  tag?: AbilityTagScore;
}

interface BubbleNode {
  radius: number;
  tag: AbilityTagScore;
  x: number;
  y: number;
}

const chartWidth = 560;
const chartHeight = 460;
const maxScore = 10;
const bubbleToneCount = 5;

export default function AbilityBubbleChart({ profile, tags }: AbilityBubbleChartProps) {
  const { locale, resources } = useI18n();
  const descriptionId = useId();
  const abilities = tags ?? profile.tags;
  const bubbles = layoutBubbles(abilities);

  return (
    <figure
      aria-labelledby={descriptionId}
      className="ability-bubbles"
      role="img"
    >
      <div
        aria-hidden="true"
        className="ability-bubbles-stage"
        onPointerLeave={resetBubbleParallax}
        onPointerMove={updateBubbleParallax}
      >
        {bubbles.map((bubble, index) => {
          const level = clamp(bubble.tag.abilityScore / maxScore, 0, 1);
          const labelLines = splitLabel(bubble.tag.label, bubble.radius >= 58 ? 10 : 7);
          const sizeClass = bubble.radius < 36 ? 'tiny' : bubble.radius < 52 ? 'compact' : 'regular';
          const motion = bubbleMotion(bubble.tag.tag, index);
          const style = {
            '--ability-level': `${level * 100}%`,
            '--bubble-delay': `${-(index % 5) * 0.7}s`,
            '--bubble-float-delay': `${motion.delay}s`,
            '--bubble-float-duration': `${motion.duration}s`,
            '--bubble-float-x-primary': `${motion.primaryX}px`,
            '--bubble-float-y-primary': `${motion.primaryY}px`,
            '--bubble-float-x-secondary': `${motion.secondaryX}px`,
            '--bubble-float-y-secondary': `${motion.secondaryY}px`,
            '--bubble-wave-back-duration': `${motion.waveBackDuration}s`,
            '--bubble-wave-front-duration': `${motion.waveFrontDuration}s`,
            left: `${((bubble.x - bubble.radius) / chartWidth) * 100}%`,
            top: `${((bubble.y - bubble.radius) / chartHeight) * 100}%`,
            width: `${((bubble.radius * 2) / chartWidth) * 100}%`,
          } as CSSProperties;

          return (
            <div
              className={`ability-bubble-node ${sizeClass} tone-${stableTone(bubble.tag.tag)}`}
              data-bubble-depth={motion.depth}
              data-level={formatScore(bubble.tag.abilityScore, locale)}
              data-testid="ability-bubble-node"
              key={bubble.tag.tag}
              style={style}
            >
              <div className={`ability-bubble-liquid ${level <= 0 ? 'empty' : ''} ${level >= 1 ? 'full' : ''}`}>
                <span className="ability-bubble-liquid-body" />
                <svg
                  className="ability-bubble-wave back"
                  focusable="false"
                  preserveAspectRatio="none"
                  viewBox="0 0 240 32"
                >
                  <path d={liquidWavePath} />
                </svg>
                <svg
                  className="ability-bubble-wave front"
                  focusable="false"
                  preserveAspectRatio="none"
                  viewBox="0 0 240 32"
                >
                  <path d={liquidWavePath} />
                  <path className="ability-bubble-wave-shine" d={liquidWaveLinePath} />
                </svg>
              </div>
              <div className="ability-bubble-content">
                <span className="ability-bubble-label" data-testid="ability-bubble-label">
                  {labelLines.map((line) => <span key={line}>{line}</span>)}
                </span>
                <strong className="ability-bubble-score">{formatScore(bubble.tag.abilityScore, locale)}</strong>
                <small className="ability-bubble-evidence">
                  {resources.myPage.reviewedProblemsValue(bubble.tag.reviewedProblemCount)}
                </small>
              </div>
            </div>
          );
        })}
      </div>
      <figcaption className="visually-hidden" id={descriptionId}>
        {resources.home.abilityMapTitle}: {abilities.map((tag) => (
          `${tag.label} ${formatScore(tag.abilityScore, locale)}, ${resources.myPage.reviewedProblemsValue(tag.reviewedProblemCount)}`
        )).join('; ')}
      </figcaption>
    </figure>
  );
}

function layoutBubbles(tags: AbilityTagScore[]): BubbleNode[] {
  if (tags.length === 0) {
    return [];
  }

  const root = hierarchy<BubbleDatum>({
    children: tags.map((tag) => ({ tag })),
  }).sum((datum) => datum.tag ? bubbleWeight(datum.tag) : 0);
  const packed = pack<BubbleDatum>()
    .size([chartWidth, chartHeight])
    .padding(18)(root);

  return packed.leaves().map((node) => ({
    radius: node.r,
    tag: node.data.tag as AbilityTagScore,
    x: node.x,
    y: node.y,
  }));
}

function bubbleWeight(tag: AbilityTagScore): number {
  return clamp(tag.reviewedProblemCount + 4, 4, 24);
}

function splitLabel(label: string, maxCharacters: number): string[] {
  const normalized = label.trim();
  if (normalized.length <= maxCharacters) {
    return [normalized];
  }

  const words = normalized.split(/\s+/);
  if (words.length > 1) {
    let firstLine = words[0];
    let wordIndex = 1;
    while (wordIndex < words.length && `${firstLine} ${words[wordIndex]}`.length <= maxCharacters) {
      firstLine = `${firstLine} ${words[wordIndex]}`;
      wordIndex += 1;
    }
    return [firstLine, truncate(words.slice(wordIndex).join(' '), maxCharacters)];
  }

  return [
    normalized.slice(0, maxCharacters),
    truncate(normalized.slice(maxCharacters), maxCharacters),
  ];
}

function truncate(value: string, maxCharacters: number): string {
  return value.length > maxCharacters ? `${value.slice(0, Math.max(1, maxCharacters - 3))}...` : value;
}

function stableTone(value: string): number {
  return stableHash(value) % bubbleToneCount;
}

const liquidWavePath = 'M 0 15 C 15 5, 30 5, 45 15 S 75 25, 90 15 S 120 5, 135 15 S 165 25, 180 15 S 210 5, 240 15 V 32 H 0 Z';
const liquidWaveLinePath = 'M 0 15 C 15 5, 30 5, 45 15 S 75 25, 90 15 S 120 5, 135 15 S 165 25, 180 15 S 210 5, 240 15';

function bubbleMotion(value: string, index: number): {
  delay: number;
  duration: number;
  primaryX: number;
  primaryY: number;
  secondaryX: number;
  secondaryY: number;
  depth: number;
  waveBackDuration: number;
  waveFrontDuration: number;
} {
  const hash = stableHash(value);
  const direction = hash % 2 === 0 ? 1 : -1;
  const primaryX = direction * (4 + (hash % 4));
  const primaryY = -(3 + ((hash >>> 3) % 4));
  return {
    delay: -round(0.5 + index * 0.38 + (hash % 7) * 0.13),
    duration: round(8.4 + (hash % 5) * 0.55),
    primaryX,
    primaryY,
    secondaryX: round(primaryX * -0.58),
    secondaryY: 2 + ((hash >>> 5) % 3),
    depth: round(0.55 + ((hash >>> 7) % 4) * 0.12),
    waveBackDuration: round(4 + ((hash >>> 9) % 5) * 0.32),
    waveFrontDuration: round(2.45 + ((hash >>> 12) % 5) * 0.22),
  };
}

function updateBubbleParallax(event: ReactPointerEvent<HTMLDivElement>): void {
  if (event.pointerType === 'touch') {
    return;
  }
  const bounds = event.currentTarget.getBoundingClientRect();
  if (bounds.width <= 0 || bounds.height <= 0) {
    return;
  }
  const horizontal = clamp(((event.clientX - bounds.left) / bounds.width - 0.5) * 2, -1, 1);
  const vertical = clamp(((event.clientY - bounds.top) / bounds.height - 0.5) * 2, -1, 1);
  applyBubbleParallax(event.currentTarget, horizontal, vertical);
}

function resetBubbleParallax(event: ReactPointerEvent<HTMLDivElement>): void {
  applyBubbleParallax(event.currentTarget, 0, 0);
}

function applyBubbleParallax(stage: HTMLDivElement, horizontal: number, vertical: number): void {
  stage.querySelectorAll<HTMLElement>('[data-bubble-depth]').forEach((bubble) => {
    const depth = Number(bubble.dataset.bubbleDepth) || 0.6;
    bubble.style.setProperty('--bubble-parallax-x', `${round(horizontal * depth * 5)}px`);
    bubble.style.setProperty('--bubble-parallax-y', `${round(vertical * depth * 3.5)}px`);
  });
}

function stableHash(value: string): number {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = ((hash << 5) - hash + value.charCodeAt(index)) | 0;
  }
  return hash >>> 0;
}

function formatScore(score: AbilityTagScore['abilityScore'], locale: string): string {
  return new Intl.NumberFormat(locale, {
    maximumFractionDigits: 1,
    minimumFractionDigits: 1,
  }).format(Number(score));
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.max(minimum, Math.min(maximum, value));
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}
