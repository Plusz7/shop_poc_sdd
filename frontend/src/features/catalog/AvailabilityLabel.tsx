import type { Schemas } from '../../api/client';
import { t } from '../../i18n/t';
import styles from './AvailabilityLabel.module.css';

type AvailabilityLabelProps = { status: Schemas['AvailabilityStatus'] };

/** Availability label derived only from the API `status` field (FR-005). */
export function AvailabilityLabel({ status }: AvailabilityLabelProps) {
  return <span className={`${styles.label} ${styles[status]}`}>{t(`availability.${status}`)}</span>;
}
