import { QueryClientProvider } from '@tanstack/react-query'
import { App as AntdApp, ConfigProvider } from 'antd'
import frFR from 'antd/locale/fr_FR'
import 'dayjs/locale/fr'
import dayjs from 'dayjs'
import { RouterProvider } from 'react-router-dom'
import './i18n'
import { queryClient } from './app/queryClient'
import { router } from './routes'

dayjs.locale('fr')

const theme = {
  token: {
    colorPrimary: '#1d4ed8',
  },
}

function App() {
  return (
    <ConfigProvider locale={frFR} theme={theme}>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  )
}

export default App
