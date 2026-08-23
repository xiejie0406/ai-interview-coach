window.MOCK={
  user:{name:'林默',goal:'Java → AI Agent 开发',level:'中级',plan:'Pro 试用',usage:'文本 8 / 10 · 语音 26 / 60 分钟'},
  questions:[
    {id:'Q-1042',title:'RAG 召回质量下降时，你会如何定位？',domain:'RAG',level:'中级',time:'8 分钟',status:'待复测',tags:['检索','评测'],source:'内部 Rubric v0.3'},
    {id:'Q-0971',title:'如何设计一个可恢复的 Agent 工作流？',domain:'工作流',level:'高级',time:'12 分钟',status:'学习中',tags:['状态机','补偿'],source:'架构实践 2026-01'},
    {id:'Q-0863',title:'Java 并发下如何保证工具调用幂等？',domain:'Java',level:'中级',time:'7 分钟',status:'已掌握',tags:['并发','幂等'],source:'Java 工程题单 v1.2'},
    {id:'Q-1132',title:'MCP Server 的权限边界如何设计？',domain:'MCP',level:'高级',time:'10 分钟',status:'未练习',tags:['权限','审计'],source:'MCP 安全清单 v0.2'},
    {id:'Q-1088',title:'如何为 LLM 应用建立 Golden Set？',domain:'评测安全',level:'中级',time:'9 分钟',status:'未练习',tags:['Golden Set','Judge'],source:'评测 Rubric v0.4'},
    {id:'Q-1017',title:'AI Agent 系统的成本与延迟如何权衡？',domain:'系统设计',level:'高级',time:'15 分钟',status:'学习中',tags:['成本','降级'],source:'系统设计题单 v0.1'}
  ],
  decisions:[
   ['首批目标用户','A Java 转 AI Agent','B 全部 Java 求职者','C 所有技术岗位','A','垂直痛点清晰，便于题库与内容冷启动','Pending'],
   ['MVP 题库范围','A Java + AI/RAG/Agent/工作流/MCP','B 只做 Agent','C 全量 Java 八股','A','覆盖转型必备路径但控制生产量','Pending'],
   ['语音是否 P0','A 级联语音 + 文本降级','B P1 再做','C 只做文本','A','验证面试场景价值，同时保留可恢复降级','Pending'],
   ['简历/JD 是否延后','A P1 延后','B 仅 JD P0','C 简历 + JD P0','A','降低敏感数据、解析和幻觉范围','Pending'],
   ['级联语音还是 Realtime','A ASR → Agent → TTS','B Realtime/WebRTC','C 浏览器语音','A','可观测、可替换、方便证据化评测','Pending'],
   ['原始音频删除策略','A 转写成功后尽快删除','B 默认保留回放','C 用户选择保留','A','数据最小化；具体 SLA 待隐私设计批准','Pending'],
   ['评分措辞','A 练习反馈 / 证据化评测','B 权威评分','C 只给建议','A','避免把模型判断包装成招聘结论','Pending'],
   ['Free / Pro 权益','A Free 试用 + Pro 额度','B 全部免费','C 一开始真实支付','A','先验证价值与单位成本，T2 接支付','Pending'],
   ['是否接真实支付','A T2 再接','B MVP 接入','C 永不支付','A','原型先展示权益与预计消耗','Pending'],
   ['是否提供匿名体验','A 公开示例，完整练习登录','B 完整匿名面试','C 完全不公开','A','降低数据归属与滥用风险','Pending'],
   ['管理后台 P0 范围','A 内容 + 质量 + 隐私 + 审计','B 仅题目 CRUD','C P1 再做','A','评分可信、故障可控、敏感访问可审计','Pending'],
   ['B2B 是否延后','A 延后','B MVP 做团队','C 教育优先','A','先验证个人闭环与留存','Pending']
  ]
};
