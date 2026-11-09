export const columns = ['TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'] as const
export type Status = typeof columns[number]
export const labels: Record<Status,string> = {TODO:'À faire', IN_PROGRESS:'En cours', REVIEW:'En revue', DONE:'Terminé'}
export type Ticket = {issueNumber:number; issueKey:string; title:string; description?:string|null; assigneeId?:number|null; priority:string; issueType:string; status:Status; version:number}
export type Board = Record<Status, Ticket[]>
export function transition(ticket: Ticket) {
  const next = columns[columns.indexOf(ticket.status) + 1]
  return next ? {newStatus:next, expectedVersion:ticket.version} : null
}
