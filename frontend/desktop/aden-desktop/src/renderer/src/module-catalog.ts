export type ModuleKey = 'CORE' | 'WX' | 'PUR' | 'COL'
export type ModuleState = 'development' | 'experimental' | 'planned'

export interface AdenModule {
  key: ModuleKey
  name: string
  summary: string
  state: ModuleState
  stateLabel: string
  nextGate: string
}

export const modules: readonly AdenModule[] = Object.freeze([
  {
    key: 'CORE',
    name: '共用执行底座',
    summary: 'RuoYi 任务状态、Runner 连接、审批、回执与证据。',
    state: 'development',
    stateLabel: '编码中',
    nextGate: '合成任务端到端回执'
  },
  {
    key: 'PUR',
    name: '采购询价助手',
    summary: '从采购需求到候选、询价、报价比较和建议快照。',
    state: 'planned',
    stateLabel: '原型已验证',
    nextGate: '接入 CORE 合成链路'
  },
  {
    key: 'COL',
    name: '电商信息采集',
    summary: '按来源授权读取商品、SKU、价格条件和证据。',
    state: 'planned',
    stateLabel: '待开发',
    nextGate: '自建页面 activeTab 探针'
  },
  {
    key: 'WX',
    name: '个人微信客服连接器',
    summary: '固定环境 UIA 读取、ERP 事实草稿与逐条复核。',
    state: 'experimental',
    stateLabel: '实验轨',
    nextGate: '只读 UIA 能力普查'
  }
])
