// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

/** Erros de domínio esperados. A UI traduz cada caso para uma mensagem; o core não carrega texto de UI. */
public sealed interface DomainError {
    /** Um campo não satisfaz uma regra de domínio. [field] é o nome do campo no modelo. */
    public data class Invalid(val field: String, val reason: Reason) : DomainError

    public data class NotFound(val entity: String, val id: String) : DomainError

    /** Backup recusado antes de qualquer escrita (ou export que não conseguiu gravar). */
    public data class BackupRejected(val reason: BackupRejection, val detail: String? = null) : DomainError

    public enum class Reason {
        REQUIRED,
        OUT_OF_RANGE,
        NOT_FINITE,
        IN_THE_FUTURE,
        END_BEFORE_START,
        NOT_POSITIVE,
        BUILTIN_IMMUTABLE,
        DUPLICATE,
    }
}

/** Motivo de recusa de um backup (Seção 7.8). Em todos os casos o banco e as mídias ficam intactos. */
public enum class BackupRejection {
    UNREADABLE,
    NOT_A_BACKUP,
    NEWER_VERSION,
    UNSUPPORTED_VERSION,
    CHECKSUM_MISMATCH,
    MISSING_MEDIA,
    INVALID_DATA,
    EXPORT_FAILED,

    /** Falha de armazenamento durante a aplicação (disco cheio, por exemplo). Nada foi gravado. */
    IMPORT_FAILED,
}
