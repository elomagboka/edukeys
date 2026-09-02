import { useState } from 'react'
import { Tag } from 'antd'
import { useTranslation } from 'react-i18next'
import { PageHeader } from '../../../shared/components/PageHeader'
import { DataTable, type DataTableColonne } from '../../../shared/components/DataTable'
import { useEtablissements } from '../api'
import { messageErreur } from '../../../api/problemDetail'
import type { components } from '../../../api/generated/schema'

type EtablissementResumeDto = components['schemas']['EtablissementResumeDto']

const TAILLE_PAR_DEFAUT = 10

export function EtablissementsPage() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const [taille, setTaille] = useState(TAILLE_PAR_DEFAUT)

  const { data, isLoading, isError, error, refetch } = useEtablissements({ page, taille })

  const colonnes: DataTableColonne<EtablissementResumeDto>[] = [
    { title: t('etablissements.colonnes.code'), dataIndex: 'code', key: 'code' },
    { title: t('etablissements.colonnes.nom'), dataIndex: 'nom', key: 'nom' },
    { title: t('etablissements.colonnes.type'), dataIndex: 'typeEtablissement', key: 'typeEtablissement' },
    { title: t('etablissements.colonnes.ville'), dataIndex: 'ville', key: 'ville' },
    { title: t('etablissements.colonnes.sites'), dataIndex: 'nombreSites', key: 'nombreSites' },
    {
      title: t('etablissements.colonnes.statut'),
      dataIndex: 'actif',
      key: 'actif',
      render: (actif: boolean) => (
        <Tag color={actif ? 'green' : 'red'}>{actif ? t('etablissements.actif') : t('etablissements.inactif')}</Tag>
      ),
    },
  ]

  return (
    <>
      <PageHeader titre={t('etablissements.titre')} />
      <DataTable<EtablissementResumeDto>
        colonnes={colonnes}
        donnees={data?.contenu ?? []}
        cleLigne="id"
        chargement={isLoading}
        erreur={isError ? error : undefined}
        messageErreur={isError ? messageErreur(error) : undefined}
        pagination={{ page, taille, total: data?.totalElements ?? 0 }}
        onChangerPage={(nouvellePage, nouvelleTaille) => {
          setPage(nouvellePage)
          setTaille(nouvelleTaille)
        }}
        onReessayer={() => void refetch()}
      />
    </>
  )
}
