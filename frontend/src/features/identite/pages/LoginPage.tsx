import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Card, Flex, Form, Input, Typography } from 'antd'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useConnexion } from '../api'
import { connexionSchema, type ConnexionFormValues } from '../schemas'
import { messageErreur } from '../../../api/problemDetail'

export function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const connexion = useConnexion()

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<ConnexionFormValues>({
    resolver: zodResolver(connexionSchema),
    defaultValues: { email: '', motDePasse: '' },
  })

  const onSoumettre = handleSubmit((valeurs) => {
    connexion.mutate(
      { email: valeurs.email, motDePasse: valeurs.motDePasse },
      { onSuccess: () => void navigate('/') },
    )
  })

  return (
    <Flex align="center" justify="center" style={{ minHeight: '100vh' }}>
      <Card style={{ width: 400 }}>
        <Typography.Title level={3} style={{ textAlign: 'center' }}>
          {t('connexion.titre')}
        </Typography.Title>
        <form onSubmit={(event) => void onSoumettre(event)} noValidate>
          <Form layout="vertical" component={false}>
            <Form.Item
              label={t('connexion.email')}
              htmlFor="email"
              validateStatus={errors.email ? 'error' : undefined}
              help={errors.email?.message}
            >
              <Controller
                name="email"
                control={control}
                render={({ field }) => <Input id="email" type="email" autoComplete="username" {...field} />}
              />
            </Form.Item>
            <Form.Item
              label={t('connexion.motDePasse')}
              htmlFor="motDePasse"
              validateStatus={errors.motDePasse ? 'error' : undefined}
              help={errors.motDePasse?.message}
            >
              <Controller
                name="motDePasse"
                control={control}
                render={({ field }) => (
                  <Input.Password id="motDePasse" autoComplete="current-password" {...field} />
                )}
              />
            </Form.Item>
            {connexion.isError && (
              <Alert
                type="error"
                showIcon
                message={messageErreur(connexion.error)}
                style={{ marginBottom: 16 }}
              />
            )}
            <Button type="primary" htmlType="submit" block loading={connexion.isPending}>
              {connexion.isPending ? t('connexion.enCours') : t('connexion.bouton')}
            </Button>
          </Form>
        </form>
      </Card>
    </Flex>
  )
}
