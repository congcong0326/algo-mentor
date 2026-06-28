import type { AbilityProfileResponse, AbilityTagScore } from '../types/api';

interface AbilityRadarChartProps {
  profile: AbilityProfileResponse;
}

const chartSize = 520;
const center = chartSize / 2;
const radius = 168;
const ticks = [2, 4, 6, 8, 10];
const maxScore = 10;

export default function AbilityRadarChart({ profile }: AbilityRadarChartProps) {
  const axes = profile.tags;
  const points = axes.map((tag, index) => pointFor(index, axes.length, scaledRadius(tag.abilityScore)));
  const polygonPoints = points.map((point) => `${point.x},${point.y}`).join(' ');

  return (
    <figure className="ability-radar" aria-label="能力雷达图" role="img">
      <svg viewBox={`0 0 ${chartSize} ${chartSize}`} aria-hidden="true" focusable="false">
        <g className="ability-radar-grid">
          {ticks.map((tick) => (
            <polygon
              key={tick}
              points={ringPoints(axes.length, scaledRadius(tick))}
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
        <polygon className="ability-radar-area" points={polygonPoints} />
        <polyline className="ability-radar-line" points={`${polygonPoints} ${points[0]?.x ?? center},${points[0]?.y ?? center}`} />
        <g className="ability-radar-points">
          {points.map((point, index) => (
            <circle key={axes[index].tag} cx={point.x} cy={point.y} r="3.5" />
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

function ringPoints(count: number, ringRadius: number): string {
  return Array.from({ length: count }, (_, index) => {
    const point = pointFor(index, count, ringRadius);
    return `${point.x},${point.y}`;
  }).join(' ');
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
