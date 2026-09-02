import { Modal } from 'antd'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

export interface FormModalProps {
  ouvert: boolean
  titre: string
  enCours?: boolean
  onSoumettre: () => void
  onFermer: () => void
  children: ReactNode
}

/** Coquille de modale de formulaire ; le formulaire (RHF + zodResolver) vit dans `children`. */
export function FormModal({ ouvert, titre, enCours, onSoumettre, onFermer, children }: FormModalProps) {
  const { t } = useTranslation()

  return (
    <Modal
      open={ouvert}
      title={titre}
      onOk={onSoumettre}
      onCancel={onFermer}
      confirmLoading={enCours}
      okText={t('action.confirmer')}
      cancelText={t('action.annuler')}
      destroyOnHidden
    >
      {children}
    </Modal>
  )
}
