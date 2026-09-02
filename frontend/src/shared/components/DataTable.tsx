import { Alert, Button, Empty, Spin, Table } from 'antd'
import type { ColumnGroupType, ColumnsType, ColumnType } from 'antd/es/table'
import { useTranslation } from 'react-i18next'

/** Pagination serveur uniquement (CLAUDE.md racine, tableaux paginés). */
export interface DataTablePagination {
  page: number
  taille: number
  total: number
}

/**
 * `ColumnType.dataIndex` d'AntD est typé `string | number | readonly (string
 * | number)[]`, pas `keyof RegistreType` : un champ renommé côté backend
 * (donc dans `api/generated/`) n'y casse pas la compilation. On restreint
 * `dataIndex` à `keyof RegistreType` pour que le contrat OpenAPI soit
 * réellement exercé par ce composant, le plus réutilisé du projet.
 */
export type DataTableColonne<RegistreType> =
  | (Omit<ColumnType<RegistreType>, 'dataIndex'> & { dataIndex?: keyof RegistreType })
  | ColumnGroupType<RegistreType>

export interface DataTableProps<RegistreType extends object> {
  colonnes: DataTableColonne<RegistreType>[]
  donnees: RegistreType[]
  cleLigne: keyof RegistreType | ((enregistrement: RegistreType) => string)
  chargement: boolean
  erreur?: unknown
  pagination: DataTablePagination
  onChangerPage: (page: number, taille: number) => void
  onReessayer?: () => void
  messageErreur?: string
}

/**
 * Tableau générique à pagination serveur : trois états obligatoires
 * (chargement, erreur, vide) en plus du cas nominal (CLAUDE.md racine).
 * Le tri et le filtrage passés par `colonnes` (onChange d'AntD) doivent être
 * relayés au serveur par l'appelant — ce composant ne trie ni ne filtre
 * lui-même côté client.
 */
export function DataTable<RegistreType extends object>({
  colonnes,
  donnees,
  cleLigne,
  chargement,
  erreur,
  pagination,
  onChangerPage,
  onReessayer,
  messageErreur,
}: DataTableProps<RegistreType>) {
  const { t } = useTranslation()

  if (erreur) {
    return (
      <Alert
        type="error"
        showIcon
        message={t('etats.erreur')}
        description={messageErreur}
        action={
          onReessayer ? (
            <Button size="small" danger onClick={onReessayer}>
              {t('etats.reessayer')}
            </Button>
          ) : undefined
        }
      />
    )
  }

  return (
    <Table<RegistreType>
      columns={colonnes as ColumnsType<RegistreType>}
      dataSource={donnees}
      rowKey={typeof cleLigne === 'function' ? cleLigne : (record) => String(record[cleLigne])}
      loading={{ spinning: chargement, indicator: <Spin /> }}
      locale={{ emptyText: <Empty description={t('etats.vide')} /> }}
      pagination={{
        current: pagination.page + 1,
        pageSize: pagination.taille,
        total: pagination.total,
        showSizeChanger: true,
        onChange: (page, taille) => onChangerPage(page - 1, taille),
      }}
    />
  )
}
