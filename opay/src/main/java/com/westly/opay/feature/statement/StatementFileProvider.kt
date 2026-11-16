package com.westly.opay.feature.statement

import androidx.core.content.FileProvider

/** A second FileProvider, with its own authority, that serves only the statement PDFs. */
class StatementFileProvider : FileProvider()
