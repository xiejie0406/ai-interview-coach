export function retryDelayMs(attempt: number, random: () => number = Math.random): number {
  const exponential = Math.min(30_000, 500 * (2 ** Math.min(Math.max(attempt, 0), 6)))
  return Math.floor(exponential * (0.75 + random() * 0.5))
}
