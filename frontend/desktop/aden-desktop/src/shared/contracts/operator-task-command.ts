/** Operator HTTP 边界当前唯一允许接受的公开 Task 命令。 */
export const OPERATOR_TASK_COMMANDS = [
  'SUBMIT_FOR_VALIDATION',
  'REQUEST_CANCEL'
] as const

export type OperatorTaskCommand = (typeof OPERATOR_TASK_COMMANDS)[number]

const operatorTaskCommandSet: ReadonlySet<string> = new Set(OPERATOR_TASK_COMMANDS)

/** 精确匹配公开命令；内部领域命令和未知枚举一律 fail closed。 */
export function parseOperatorTaskCommand(value: unknown): OperatorTaskCommand {
  if (typeof value !== 'string' || !operatorTaskCommandSet.has(value)) {
    throw new TypeError('未知或非公开的 OperatorTaskCommand')
  }
  return value as OperatorTaskCommand
}
