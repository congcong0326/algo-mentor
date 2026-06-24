import { ArrowRight, BrainCircuit, CalendarCheck, Library, ListChecks, Sparkles } from 'lucide-react';
import type { AppView } from './app/navigation';

interface HomeDashboardProps {
  onNavigate: (view: AppView) => void;
}

const featureCards = [
  {
    title: '训练方案',
    description: '按目标、时间和强弱项生成阶段安排，把刷题节奏拆成可执行任务。',
    icon: CalendarCheck,
  },
  {
    title: '题库训练',
    description: '集中管理题目、难度和标签，快速进入当前最该练的一类问题。',
    icon: Library,
  },
  {
    title: 'AI 讲解',
    description: '围绕思路、边界条件和复杂度追问，让每道题沉淀成可复用模式。',
    icon: BrainCircuit,
  },
];

const workflowSteps = [
  ['1', '选题', '从方案或题库选择本轮重点。'],
  ['2', '练习', '先独立推导，再记录阻塞点。'],
  ['3', '讲解', '用 AI 补齐思路、模板和边界。'],
  ['∞', '复盘', '按方案回看，避免刷完就忘。'],
];

export default function HomeDashboard({ onNavigate }: HomeDashboardProps) {
  return (
    <section className="home-page" aria-label="首页">
      <section className="home-hero" aria-labelledby="home-title">
        <div className="home-hero-copy">
          <p className="home-kicker">ALGORITHM LEARNING SYSTEM</p>
          <h1 id="home-title">把算法练习变成可复盘的学习系统</h1>
          <p className="home-subtitle">
            Algo Mentor 把训练方案、题库训练和 AI 讲解放在同一个工作台里，帮助你持续练习、及时复盘、沉淀题型方法。
          </p>
          <div className="home-hero-actions">
            <button className="primary-button hero-action" onClick={() => onNavigate('learningPlans')} type="button">
              <Sparkles aria-hidden="true" />
              <span>生成训练方案</span>
            </button>
            <button className="secondary-button hero-action" onClick={() => onNavigate('problems')} type="button">
              <Library aria-hidden="true" />
              <span>浏览题库</span>
            </button>
          </div>
        </div>

        <div className="home-preview" aria-label="学习工作台预览">
          <div className="preview-window">
            <div className="preview-toolbar" aria-hidden="true">
              <span />
              <span />
              <span />
            </div>
            <div className="preview-content">
              <div className="preview-summary">
                <span>本周重点</span>
                <strong>数组、哈希表、双指针</strong>
              </div>
              <div className="preview-task active">
                <ListChecks aria-hidden="true" />
                <span>完成 5 道基础题</span>
                <strong>进行中</strong>
              </div>
              <div className="preview-task">
                <BrainCircuit aria-hidden="true" />
                <span>复盘 Two Sum 思路</span>
                <strong>今天</strong>
              </div>
              <div className="preview-task">
                <CalendarCheck aria-hidden="true" />
                <span>更新下周训练方案</span>
                <strong>周日</strong>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="home-section" aria-labelledby="home-capabilities-title">
        <div className="home-section-header">
          <p className="eyebrow">CAPABILITIES</p>
          <h2 id="home-capabilities-title">高频训练入口放在第一屏之后</h2>
        </div>
        <div className="home-card-grid">
          {featureCards.map((card) => {
            const Icon = card.icon;
            return (
              <article className="home-feature-card" key={card.title}>
                <Icon aria-hidden="true" />
                <h3>{card.title}</h3>
                <p>{card.description}</p>
              </article>
            );
          })}
        </div>
      </section>

      <section className="home-section" aria-labelledby="home-workflow-title">
        <div className="home-section-header">
          <p className="eyebrow">LEARNING LOOP</p>
          <h2 id="home-workflow-title">一套简单循环，长期记住题型</h2>
        </div>
        <ol className="home-workflow" aria-label="算法学习闭环">
          {workflowSteps.map(([index, title, description]) => (
            <li className="home-workflow-step" key={title}>
              <span className="workflow-index" aria-hidden="true">{index}</span>
              <strong>{title}</strong>
              <span>{description}</span>
            </li>
          ))}
        </ol>
      </section>

      <section className="home-cta" aria-label="开始学习">
        <div>
          <h2>从一份方案开始今天的训练</h2>
          <p>先确定目标和时间，再让系统给出阶段、题目和复盘建议。</p>
        </div>
        <button className="primary-button hero-action" onClick={() => onNavigate('learningPlans')} type="button">
          <span>进入训练方案</span>
          <ArrowRight aria-hidden="true" />
        </button>
      </section>
    </section>
  );
}
