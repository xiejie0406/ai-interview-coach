"""兼容导入入口；唯一实现位于 ports.py。"""

from .ports import DisabledModelPort, FakeModelPort, ModelDraft

__all__ = ["DisabledModelPort", "FakeModelPort", "ModelDraft"]
