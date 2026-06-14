import {
  BookOpenCheck,
  Brain,
  CalendarDays,
  ChartNoAxesCombined,
  CircleCheck,
  ClipboardList,
  Flame,
  MessageSquareText,
  RotateCcw,
  Search,
  Timer,
} from 'lucide-react';

const practiceItems = [
  { title: '两数之和变体', difficulty: 'Easy', state: '待练习', accent: 'green' },
  { title: '滑动窗口最大值', difficulty: 'Hard', state: '重点复盘', accent: 'red' },
  { title: '最长递增子序列', difficulty: 'Medium', state: '进行中', accent: 'amber' },
];

const reviewItems = [
  { title: '边界条件遗漏', count: 6 },
  { title: '复杂度估算偏差', count: 3 },
  { title: '状态转移不完整', count: 4 },
];

export default function App() {
  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="主导航">
        <div className="brand">
          <Brain aria-hidden="true" />
          <span>Algo Mentor</span>
        </div>
        <nav className="nav-list">
          <button className="nav-item active" title="今日训练" aria-label="今日训练">
            <Flame aria-hidden="true" />
          </button>
          <button className="nav-item" title="题库" aria-label="题库">
            <ClipboardList aria-hidden="true" />
          </button>
          <button className="nav-item" title="AI 讲解" aria-label="AI 讲解">
            <MessageSquareText aria-hidden="true" />
          </button>
          <button className="nav-item" title="错题复盘" aria-label="错题复盘">
            <RotateCcw aria-hidden="true" />
          </button>
        </nav>
      </aside>

      <main className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">学习工作台</p>
            <h1>今日训练</h1>
          </div>
          <div className="search-box">
            <Search aria-hidden="true" />
            <input aria-label="搜索题目" placeholder="搜索题目" />
          </div>
        </header>

        <section className="metrics" aria-label="学习指标">
          <article className="metric-card">
            <CalendarDays aria-hidden="true" />
            <div>
              <span>连续学习</span>
              <strong>12 天</strong>
            </div>
          </article>
          <article className="metric-card">
            <Timer aria-hidden="true" />
            <div>
              <span>今日投入</span>
              <strong>48 分钟</strong>
            </div>
          </article>
          <article className="metric-card">
            <ChartNoAxesCombined aria-hidden="true" />
            <div>
              <span>掌握率</span>
              <strong>76%</strong>
            </div>
          </article>
        </section>

        <div className="content-grid">
          <section className="panel practice-panel" aria-labelledby="practice-title">
            <div className="panel-header">
              <div>
                <p className="eyebrow">队列</p>
                <h2 id="practice-title">题目列表</h2>
              </div>
              <button className="icon-button" title="完成当前题" aria-label="完成当前题">
                <CircleCheck aria-hidden="true" />
              </button>
            </div>
            <div className="problem-list">
              {practiceItems.map((item) => (
                <article className="problem-card" key={item.title}>
                  <span className={`status-dot ${item.accent}`} aria-hidden="true" />
                  <div>
                    <h3>{item.title}</h3>
                    <p>{item.difficulty}</p>
                  </div>
                  <span className="state-label">{item.state}</span>
                </article>
              ))}
            </div>
          </section>

          <section className="panel ai-panel" aria-labelledby="ai-title">
            <div className="panel-header">
              <div>
                <p className="eyebrow">助手</p>
                <h2 id="ai-title">AI 讲解</h2>
              </div>
              <BookOpenCheck aria-hidden="true" />
            </div>
            <div className="explain-box">
              <p>双指针适合在有序区间内维护两个移动边界。</p>
              <button>生成讲解</button>
            </div>
          </section>

          <section className="panel review-panel" aria-labelledby="review-title">
            <div className="panel-header">
              <div>
                <p className="eyebrow">复盘</p>
                <h2 id="review-title">错题复盘</h2>
              </div>
              <RotateCcw aria-hidden="true" />
            </div>
            <div className="review-list">
              {reviewItems.map((item) => (
                <article className="review-row" key={item.title}>
                  <span>{item.title}</span>
                  <strong>{item.count}</strong>
                </article>
              ))}
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}

