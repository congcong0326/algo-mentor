import type { AbilityProfileResponse, AbilityTagScore } from '../types/api';

interface AbilityRadarChartProps {
  profile: AbilityProfileResponse;
  tags?: AbilityTagScore[];
}

const chartSize = 520;
const center = chartSize / 2;
const radius = 168;
const ticks = [2, 4, 6, 8, 10];
const maxScore = 10;

export default function AbilityRadarChart({ profile, tags }: AbilityRadarChartProps) {
  const axes = tags ?? profile.tags;
  const petalAngle = axes.length > 0 ? (Math.PI * 2) / axes.length : 0;

  return (
    <figure className="ability-radar" aria-label="能力雷达图" role="img">
      <svg viewBox={`0 0 ${chartSize} ${chartSize}`} aria-hidden="true" focusable="false">
        <g className="ability-radar-grid">
          {ticks.map((tick) => (
            <circle
              cx={center}
              cy={center}
              key={tick}
              r={scaledRadius(tick)}
            />
          ))}
          {axes.map((tag, index) => {
            const outer = pointFor(index, axes.length, radius);
            return (
              <line
                key={tag.tag}
                x1={center}
                y1={center}
                x2={outer.x}
                y2={outer.y}
              />
            );
          })}
        </g>
        <g className="ability-radar-ticks">
          {ticks.map((tick) => (
            <text key={tick} x={center + 8} y={center - scaledRadius(tick) + 4}>
              {tick}
            </text>
          ))}
        </g>
        <g className="ability-rose-petals">
          {axes.map((tag, index) => (
            <path
              d={petalPath(index, axes.length, scaledRadius(tag.abilityScore), petalAngle)}
              data-testid="ability-rose-petal"
              key={tag.tag}
            />
          ))}
        </g>
        <g className="ability-radar-labels">
          {axes.map((tag, index) => {
            const labelPoint = pointFor(index, axes.length, radius + 42);
            const valuePoint = pointFor(index, axes.length, radius + 62);
            return (
              <g key={tag.tag}>
                <text
                  data-testid="ability-radar-axis-label"
                  x={labelPoint.x}
                  y={labelPoint.y}
                  textAnchor={textAnchor(labelPoint.x)}
                >
                  {tag.label}
                </text>
                <text
                  className="ability-radar-score"
                  x={valuePoint.x}
                  y={valuePoint.y}
                  textAnchor={textAnchor(valuePoint.x)}
                >
                  {formatScore(tag.abilityScore)}
                </text>
              </g>
            );
          })}
        </g>
      </svg>
    </figure>
  );
}

function pointFor(index: number, count: number, pointRadius: number): { x: number; y: number } {
  if (count === 0) {
    return { x: center, y: center };
  }
  const angle = (Math.PI * 2 * index) / count - Math.PI / 2;
  return {
    x: round(center + Math.cos(angle) * pointRadius),
    y: round(center + Math.sin(angle) * pointRadius),
  };
}

function scaledRadius(score: number): number {
  return (Math.max(0, Math.min(maxScore, score)) / maxScore) * radius;
}

function petalPath(index: number, count: number, petalRadius: number, petalAngle: number): string {
  if (count === 0 || petalRadius <= 0) {
    return `M ${center} ${center}`;
  }
  const angle = (Math.PI * 2 * index) / count - Math.PI / 2;
  const halfAngle = Math.max(0.08, petalAngle * 0.36);
  const start = polarPoint(angle - halfAngle, Math.max(16, petalRadius * 0.22));
  const tip = polarPoint(angle, petalRadius);
  const end = polarPoint(angle + halfAngle, Math.max(16, petalRadius * 0.22));
  const controlLeft = polarPoint(angle - halfAngle * 0.52, petalRadius * 0.88);
  const controlRight = polarPoint(angle + halfAngle * 0.52, petalRadius * 0.88);
  return [
    `M ${center} ${center}`,
    `L ${start.x} ${start.y}`,
    `C ${controlLeft.x} ${controlLeft.y} ${tip.x} ${tip.y} ${tip.x} ${tip.y}`,
    `C ${tip.x} ${tip.y} ${controlRight.x} ${controlRight.y} ${end.x} ${end.y}`,
    'Z',
  ].join(' ');
}

function polarPoint(angle: number, pointRadius: number): { x: number; y: number } {
  return {
    x: round(center + Math.cos(angle) * pointRadius),
    y: round(center + Math.sin(angle) * pointRadius),
  };
}

function textAnchor(x: number): 'start' | 'middle' | 'end' {
  if (Math.abs(x - center) < 12) {
    return 'middle';
  }
  return x > center ? 'start' : 'end';
}

function formatScore(score: AbilityTagScore['abilityScore']): string {
  return Number(score).toFixed(1);
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}
