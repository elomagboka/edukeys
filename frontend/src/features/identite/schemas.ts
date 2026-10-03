import { z } from 'zod'

export const connexionSchema = z.object({
  identifiant: z.string().min(1, "L'identifiant est requis."),
  motDePasse: z.string().min(1, 'Le mot de passe est requis.'),
})

export type ConnexionFormValues = z.infer<typeof connexionSchema>
