import { BankOutlined, LogoutOutlined } from '@ant-design/icons'
import { Button, Layout, Menu, Typography } from 'antd'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useSessionStore } from '../stores/session'
import type { Role } from '../shared/types/roles'

const { Header, Sider, Content } = Layout

interface ElementMenu {
  cle: string
  chemin: string
  libelle: string
  icone: React.ReactNode
  rolesAutorises: Role[]
}

export function AppLayout() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const roles = useSessionStore((etat) => etat.roles)
  const effacerSession = useSessionStore((etat) => etat.effacerSession)

  const elementsMenu: ElementMenu[] = useMemo(
    () => [
      {
        cle: 'etablissements',
        chemin: '/etablissements',
        libelle: t('menu.etablissements'),
        icone: <BankOutlined />,
        rolesAutorises: ['SUPER_ADMIN'],
      },
    ],
    [t],
  )

  const elementsVisibles = elementsMenu.filter((element) =>
    element.rolesAutorises.some((role) => roles.includes(role)),
  )

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsible>
        <div style={{ color: 'white', padding: 16, fontWeight: 'bold', fontSize: 18 }}>Edukeys</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={elementsVisibles.map((element) => ({
            key: element.chemin,
            icon: element.icone,
            label: <Link to={element.chemin}>{element.libelle}</Link>,
          }))}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography.Text strong>Edukeys</Typography.Text>
          <Button
            icon={<LogoutOutlined />}
            onClick={() => {
              effacerSession()
              void navigate('/connexion')
            }}
          >
            Déconnexion
          </Button>
        </Header>
        <Content style={{ margin: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
