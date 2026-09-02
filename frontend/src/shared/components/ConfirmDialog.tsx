import { Modal } from 'antd'
import { useTranslation } from 'react-i18next'

export interface ConfirmDialogProps {
  ouvert: boolean
  titre: string
  description?: string
  danger?: boolean
  enCours?: boolean
  onConfirmer: () => void
  onAnnuler: () => void
}

/** Boîte de confirmation accessible au clavier (focus géré par AntD Modal). */
export function ConfirmDialog({
  ouvert,
  titre,
  description,
  danger,
  enCours,
  onConfirmer,
  onAnnuler,
}: ConfirmDialogProps) {
  const { t } = useTranslation()

  return (
    <Modal
      open={ouvert}
      title={titre}
      onOk={onConfirmer}
      onCancel={onAnnuler}
      confirmLoading={enCours}
      okText={t('action.confirmer')}
      cancelText={t('action.annuler')}
      okButtonProps={{ danger }}
    >
      {description}
    </Modal>
  )
}
