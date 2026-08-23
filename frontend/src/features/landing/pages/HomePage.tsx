import { Link } from 'react-router-dom';
import { PageFrame } from '../../../shared/components/PageFrame';

export function HomePage() {
  return <PageFrame eyebrow="JAVA → AI AGENT" title="把面试练习，变成可复盘的成长闭环" description="从版本化题库开始，完成文本或级联语音模拟面试，看到有证据的反馈，再把弱项变成下一次练习。">
    <section className="hero-grid"><div className="hero-copy"><div className="hero-actions"><Link to="/questions" className="button button-primary">浏览题库</Link><Link to="/app/interviews/new" className="button button-secondary">开始模拟面试</Link></div><p className="muted">语音需要单独授权；无法使用麦克风时，流程完整降级到文本。</p></div><div className="hero-card"><span className="card-kicker">EVIDENCE FIRST</span><strong>先给证据，再给判断</strong><ol><li>固定题目与 Rubric 版本</li><li>引用已确认的回答片段</li><li>证据不足时明确保留意见</li></ol><small>这里不展示示例分数或伪造趋势。</small></div></section>
    <section className="feature-grid"><article className="feature-card"><span className="feature-index">01</span><h2>先答，再看</h2><p>题目、Rubric 和来源版本固定，避免答案被一次生成悄悄改写。</p></article><article className="feature-card"><span className="feature-index">02</span><h2>语音面试</h2><p>使用 RuoYi 登录主体进入语音面试；麦克风、断线或 Provider 失败时可恢复到文字。</p><Link to="/app/interviews/new?mode=voice" className="text-link">进入语音面试 →</Link></article><article className="feature-card"><span className="feature-index">03</span><h2>反馈可回读</h2><p>每个判断都尽量回到你的原话；证据不足时明确说“不确定”。</p></article></section>
    <section className="callout"><div><span className="eyebrow">SAFE PRACTICE</span><h2>这是练习教练，不是隐蔽的真实面试代答工具。</h2></div><Link to="/app/settings/privacy" className="text-link">了解数据与隐私 →</Link></section>
  </PageFrame>;
}
