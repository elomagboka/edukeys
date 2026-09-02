import { Breadcrumb, Flex, Typography } from 'antd'
import type { ReactNode } from 'react'

export interface PageHeaderProps {
  titre: string
  filAriane?: { libelle: string; lien?: string }[]
  actions?: ReactNode
}

export function PageHeader({ titre, filAriane, actions }: PageHeaderProps) {
  return (
    <Flex vertical gap="small" style={{ marginBottom: 16 }}>
      {filAriane && filAriane.length > 0 && (
        <Breadcrumb
          items={filAriane.map((item) => ({ title: item.lien ? <a href={item.lien}>{item.libelle}</a> : item.libelle }))}
        />
      )}
      <Flex align="center" justify="space-between">
        <Typography.Title level={3} style={{ margin: 0 }}>
          {titre}
        </Typography.Title>
        {actions}
      </Flex>
    </Flex>
  )
}
