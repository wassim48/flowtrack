import {describe,it,expect} from 'vitest'
import {transition, type Ticket} from './board'
const ticket:Ticket = {issueNumber:4,issueKey:'APP-4',title:'Connexion',priority:'HIGH',issueType:'BUG',status:'IN_PROGRESS',version:8}
describe('déplacement', () => {
  it('envoie le statut suivant et la version affichée', () => expect(transition(ticket)).toEqual({newStatus:'REVIEW',expectedVersion:8}))
  it('termine le parcours dans la dernière colonne', () => expect(transition({...ticket,status:'DONE'})).toBeNull())
})
