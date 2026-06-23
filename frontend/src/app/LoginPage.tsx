import { LogIn } from 'lucide-react';

export interface LoginPageProps {
  authFailed?: boolean;
}

export default function LoginPage({ authFailed = false }: LoginPageProps) {
  return (
    <main className="login-page" aria-labelledby="login-title">
      <section className="login-panel">
        <p className="home-kicker">ALGO MENTOR</p>
        <h1 id="login-title">Algo Mentor</h1>
        <p className="login-subtitle">算法学习、刷题训练和 AI 学习计划生成工具</p>
        {authFailed && <p className="error-text">登录失败，请重新尝试。</p>}
        <a className="primary-button login-oauth-link" href="/oauth2/authorization/google">
          <LogIn aria-hidden="true" />
          <span>使用 Google 登录</span>
        </a>
      </section>
    </main>
  );
}
