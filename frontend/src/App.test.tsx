import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import App from './App'
import { useSessionStore } from './stores/session'

describe('App', () => {
  beforeEach(() => {
    useSessionStore.getState().effacerSession()
    window.history.pushState({}, '', '/connexion')
  })

  it("affiche le formulaire de connexion sous le nom Edukeys", () => {
    render(<App />)
    expect(screen.getByText('Connexion à Edukeys')).toBeInTheDocument()
  })

  it('connecte un compte SUPER_ADMIN et affiche la liste paginée des établissements', async () => {
    const utilisateur = userEvent.setup()
    render(<App />)

    await utilisateur.type(screen.getByLabelText('Adresse email'), 'super.admin@edukeys.tg')
    await utilisateur.type(screen.getByLabelText('Mot de passe'), 'Password123!')
    await utilisateur.click(screen.getByRole('button', { name: 'Se connecter' }))

    await waitFor(() => {
      expect(screen.getByText('Établissement Démo')).toBeInTheDocument()
    })
    expect(screen.getByText('EDU-001')).toBeInTheDocument()
  })

  it('affiche un message d’erreur sur des identifiants invalides', async () => {
    const utilisateur = userEvent.setup()
    render(<App />)

    await utilisateur.type(screen.getByLabelText('Adresse email'), 'inconnu@edukeys.tg')
    await utilisateur.type(screen.getByLabelText('Mot de passe'), 'mauvais')
    await utilisateur.click(screen.getByRole('button', { name: 'Se connecter' }))

    await waitFor(() => {
      expect(screen.getByText('Identifiants invalides.')).toBeInTheDocument()
    })
  })
})
