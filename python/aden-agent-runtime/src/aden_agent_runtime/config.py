"""兼容 IMP-01 配置导入；唯一模型位于 bootstrap.settings。"""

from aden_agent_runtime.bootstrap import RuntimeSettings

AgentRuntimeBaselineSettings = RuntimeSettings

__all__ = ["AgentRuntimeBaselineSettings"]
