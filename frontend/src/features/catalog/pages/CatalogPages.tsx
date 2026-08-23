import { useState, type FormEvent } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { catalogApi, serializeQuestionFilters, type Difficulty } from '../api/catalogApi';
import { queryKeys } from '../../../shared/api/queryKeys';
import { AsyncState } from '../../../shared/components/AsyncState';
import { PageFrame } from '../../../shared/components/PageFrame';
import { useSession } from '../../../shared/session/SessionProvider';

const categoryLabels: Record<string, string> = {
  AGENT_BASICS_DEEP_V3: 'Agent 基础',
  LLM_FOUNDATION_DEEP_V3: '大模型基础',
  PROMPT_ENGINEERING_DEEP_V3: 'Prompt Engineering',
  RAG_DEEP_V3: 'RAG 检索增强生成',
  KNOWLEDGE_BASE_DEEP_V3: '知识库',
  WORKFLOW_DEEP_V3: '知识库工作流',
  TOOL_CALLING_DEEP_V3: '工具调用',
  MEMORY_DEEP_V3: 'Agent Memory',
  MULTI_AGENT_DEEP_V3: '多 Agent 协作',
  EVALUATION_DEEP_V3: 'Agent 评测',
  AGENT_SECURITY_DEEP_V3: 'Agent 安全',
  AGENT_ENGINEERING_DEEP_V3: 'Agent 工程化',
};

const modules = Object.entries(categoryLabels).map(([key, label]) => ({ key, label }));
const moduleShortLabels: Record<string, string> = {
  AGENT_BASICS_DEEP_V3: 'AG', LLM_FOUNDATION_DEEP_V3: 'LLM', PROMPT_ENGINEERING_DEEP_V3: 'P', RAG_DEEP_V3: 'RAG',
  KNOWLEDGE_BASE_DEEP_V3: 'KB', WORKFLOW_DEEP_V3: 'WF', TOOL_CALLING_DEEP_V3: 'TOOL', MEMORY_DEEP_V3: 'MEM',
  MULTI_AGENT_DEEP_V3: 'MA', EVALUATION_DEEP_V3: 'EVAL', AGENT_SECURITY_DEEP_V3: 'SAFE', AGENT_ENGINEERING_DEEP_V3: 'ENG',
};

const difficultyLabels: Record<Difficulty, string> = {
  JUNIOR: '初级',
  MID: '中级',
  SENIOR: '高级',
};

function categoryLabel(category: string) {
  return categoryLabels[category] ?? category;
}

export function QuestionListPage() {
  const { queryScope } = useSession();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [moduleCollapsed, setModuleCollapsed] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [editingAnswer, setEditingAnswer] = useState(false);
  const [viewingSystemAnswer, setViewingSystemAnswer] = useState(false);
  const [answerDraft, setAnswerDraft] = useState('');
  const [newTitle, setNewTitle] = useState('');
  const [newPrompt, setNewPrompt] = useState('');
  const [newAnswer, setNewAnswer] = useState('');
  const activeQuery = searchParams.get('query') ?? '';
  const activeCategory = searchParams.get('category') ?? '';
  const selectedQuestionId = searchParams.get('question') ?? '';
  const difficultyParam = searchParams.get('difficulty');
  const activeDifficulty: Difficulty | '' = difficultyParam === 'JUNIOR' || difficultyParam === 'MID' || difficultyParam === 'SENIOR' ? difficultyParam : '';
  const cursor = searchParams.get('cursor') ?? undefined;
  const [draftQuery, setDraftQuery] = useState(activeQuery);
  const [draftDifficulty, setDraftDifficulty] = useState<Difficulty | ''>(activeDifficulty);
  const serialized = serializeQuestionFilters({ query: activeQuery, category: activeCategory || undefined, difficulty: activeDifficulty || undefined, cursor, limit: 20 });
  const result = useQuery({
    queryKey: queryKeys.catalog.questions(queryScope, serialized),
    queryFn: ({ signal }) => catalogApi.search({ query: activeQuery, category: activeCategory || undefined, difficulty: activeDifficulty || undefined, cursor, limit: 20 }, signal),
    enabled: Boolean(activeCategory),
  });
  const detail = useQuery({
    queryKey: queryKeys.catalog.question(queryScope, selectedQuestionId || 'missing'),
    queryFn: ({ signal }) => catalogApi.getQuestion(selectedQuestionId, signal),
    enabled: Boolean(selectedQuestionId),
  });
  const createQuestion = useMutation({
    mutationFn: () => catalogApi.createQuestion({ category: activeCategory, difficulty: 'MID', title: newTitle.trim(), prompt: newPrompt.trim(), systemAnswer: newAnswer.trim() }),
    onSuccess: async (created) => {
      setShowCreate(false); setNewTitle(''); setNewPrompt(''); setNewAnswer('');
      await queryClient.invalidateQueries({ queryKey: queryKeys.catalog.questions(queryScope, serialized) });
      chooseQuestion(created.id);
    },
  });
  const saveAnswer = useMutation({
    mutationFn: () => catalogApi.saveMyAnswer(selectedQuestionId, answerDraft.trim()),
    onSuccess: async () => {
      setEditingAnswer(false); setViewingSystemAnswer(false);
      await queryClient.invalidateQueries({ queryKey: queryKeys.catalog.question(queryScope, selectedQuestionId) });
    },
  });

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    const next = new URLSearchParams();
    if (activeCategory) next.set('category', activeCategory);
    if (draftQuery.trim()) next.set('query', draftQuery.trim());
    if (draftDifficulty) next.set('difficulty', draftDifficulty);
    setSearchParams(next);
  }

  function chooseModule(key: string) {
    const next = new URLSearchParams({ category: key });
    setSearchParams(next);
  }

  function chooseQuestion(id: string) {
    const next = new URLSearchParams(searchParams);
    next.set('question', id);
    setSearchParams(next);
    setEditingAnswer(false);
    setViewingSystemAnswer(false);
  }

  const page = result.data;
  const selected = detail.data?.data;

  return (
    <div className="qbank-page">
      <div className={`qbank-workbench${moduleCollapsed ? ' modules-collapsed' : ''}`}>
        <aside className="qbank-modules">
          <div className="qbank-column-header"><span>模块 · {modules.length}</span><button type="button" className="button button-ghost button-small" aria-label={moduleCollapsed ? '展开模块列表' : '收缩模块列表'} onClick={() => setModuleCollapsed((value) => !value)}>{moduleCollapsed ? '展开' : '收缩'}</button></div>
          {modules.map((module) => <button type="button" className={`qbank-module${activeCategory === module.key ? ' active' : ''}`} aria-label={module.label} onClick={() => chooseModule(module.key)} key={module.key}><b>{moduleShortLabels[module.key]}</b><span>{module.label}</span><small>50 题</small></button>)}
        </aside>
        <section className="qbank-questions">
          <div className="qbank-column-header"><span>{activeCategory ? `${categoryLabel(activeCategory)} · 50 题` : '请选择一个模块'}</span>{activeCategory && <button type="button" className="button button-secondary button-small" onClick={() => setShowCreate(true)}>+ 添加</button>}</div>
          {activeCategory && <form className="qbank-search" onSubmit={applyFilters}><label className="sr-only" htmlFor="question-search">搜索本模块题目</label><input id="question-search" placeholder="搜索本模块题目" value={draftQuery} onChange={(event) => setDraftQuery(event.target.value)} /><select aria-label="难度筛选" value={draftDifficulty} onChange={(event) => setDraftDifficulty(event.target.value as Difficulty | '')}><option value="">全部难度</option><option value="JUNIOR">初级</option><option value="MID">中级</option><option value="SENIOR">高级</option></select></form>}
          {!activeCategory && <p className="qbank-empty">从左侧选择模块，开始浏览 50 道题目。</p>}
          {activeCategory && <AsyncState loading={result.isPending} error={result.error} empty={!result.isPending && !result.error && !page?.items.length} publicResource onRetry={() => void result.refetch()}><div className="qbank-question-list">{(page?.items ?? []).map((question) => <button type="button" className={`qbank-question${selectedQuestionId === question.id ? ' active' : ''}`} onClick={() => chooseQuestion(question.id)} key={question.versionId}><span>{question.title}</span><small>{difficultyLabels[question.difficulty]}</small></button>)}</div><div className="pagination-actions">{cursor && <button type="button" className="button button-ghost" onClick={() => { const next = new URLSearchParams(searchParams); next.delete('cursor'); setSearchParams(next); }}>第一页</button>}{page?.hasMore && page.nextCursor && <button type="button" className="button button-secondary" onClick={() => { const next = new URLSearchParams(searchParams); next.set('cursor', page.nextCursor!); setSearchParams(next); }}>下一页</button>}</div></AsyncState>}
        </section>
        <article className="qbank-answer">
          {!selected && <div className="qbank-empty"><span className="eyebrow">STANDARD ANSWER</span><h2>选择一道题</h2><p>点击中间的题目，在这里阅读完整标准答案。</p></div>}
          {selected && <AsyncState loading={detail.isPending} error={detail.error} publicResource onRetry={() => void detail.refetch()}><div className="qbank-answer-heading"><div><span className="card-kicker">{categoryLabel(selected.category)} · {difficultyLabels[selected.difficulty]}</span><h2>{selected.title}</h2></div><div className="qbank-answer-actions">{!editingAnswer && <button type="button" className="button button-secondary button-small" onClick={() => { setAnswerDraft(selected.referenceAnswer.join('\n\n')); setEditingAnswer(true); setViewingSystemAnswer(false); }}>编辑答案</button>}{!editingAnswer && selected.hasUserAnswer && <button type="button" className="button button-ghost button-small" onClick={() => setViewingSystemAnswer((value) => !value)}>{viewingSystemAnswer ? '返回答案' : '查看系统答案'}</button>}</div></div><p className="question-body">{selected.prompt}</p>{editingAnswer ? <div className="qbank-answer-editor"><textarea rows={18} value={answerDraft} onChange={(event) => setAnswerDraft(event.target.value)} />{saveAnswer.isError && <p className="inline-error">保存失败：{saveAnswer.error instanceof Error ? saveAnswer.error.message : '请稍后重试'}</p>}<div className="form-actions"><button type="button" className="button button-ghost" onClick={() => setEditingAnswer(false)}>取消</button><button type="button" className="button button-primary" disabled={!answerDraft.trim() || saveAnswer.isPending} onClick={() => saveAnswer.mutate()}>{saveAnswer.isPending ? '保存中…' : '保存'}</button></div></div> : <div className="answer-content">{(viewingSystemAnswer ? selected.systemAnswer : selected.referenceAnswer).flatMap((answer) => answer.split('\n\n')).map((paragraph) => <p key={paragraph}>{paragraph}</p>)}</div>}</AsyncState>}
        </article>
      </div>
      {showCreate && <div className="qbank-dialog-backdrop" role="presentation"><section className="qbank-dialog" role="dialog" aria-modal="true" aria-labelledby="create-question-title"><h2 id="create-question-title">添加题目</h2><p className="muted">题目和基础答案保存后进入当前公共模块。</p><label>题目标题<input value={newTitle} maxLength={500} onChange={(event) => setNewTitle(event.target.value)} /></label><label>题目描述<textarea rows={5} value={newPrompt} maxLength={8000} onChange={(event) => setNewPrompt(event.target.value)} /></label><label>基础答案<textarea rows={10} value={newAnswer} maxLength={20000} onChange={(event) => setNewAnswer(event.target.value)} /></label><div className="form-actions"><button type="button" className="button button-ghost" onClick={() => setShowCreate(false)}>取消</button><button type="button" className="button button-primary" disabled={!newTitle.trim() || !newPrompt.trim() || !newAnswer.trim() || createQuestion.isPending} onClick={() => createQuestion.mutate()}>{createQuestion.isPending ? '保存中…' : '保存题目'}</button></div></section></div>}
    </div>
  );
}

export function QuestionDetailPage() {
  const { questionId } = useParams();
  const { queryScope } = useSession();
  const result = useQuery({
    queryKey: queryKeys.catalog.question(queryScope, questionId ?? 'missing'),
    queryFn: ({ signal }) => catalogApi.getQuestion(questionId!, signal),
    enabled: Boolean(questionId),
  });
  const question = result.data?.data;
  return (
    <PageFrame eyebrow="STANDARD ANSWER" title={question?.title ?? '题目详情'} description="本页展示题目与标准答案。">
      <AsyncState loading={result.isPending} error={result.error} publicResource onRetry={() => void result.refetch()}>
        {question && <div className="panel question-detail">
          <span className="card-kicker">{categoryLabel(question.category)} · {difficultyLabels[question.difficulty]} · 版本 {question.version}</span>
          <p className="question-body">{question.prompt}</p>
          <section><h2>标准答案</h2><div className="answer-box">{question.referenceAnswer.map((answer) => <p key={answer}>{answer}</p>)}</div></section>
        </div>}
      </AsyncState>
    </PageFrame>
  );
}
