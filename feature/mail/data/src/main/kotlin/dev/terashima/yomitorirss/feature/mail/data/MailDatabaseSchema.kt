package dev.terashima.yomitorirss.feature.mail.data

import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val mailDatabaseSchema = DatabaseSchemaContribution(
  owner = "mail",
  createSchema = { db ->
    db.execSQL("CREATE TABLE IF NOT EXISTS mail_accounts(id TEXT PRIMARY KEY NOT NULL,email TEXT NOT NULL UNIQUE,display_name TEXT,last_history_id TEXT,last_synced_at INTEGER,sync_state TEXT NOT NULL DEFAULT 'idle',sync_processed_threads INTEGER NOT NULL DEFAULT 0,sync_error TEXT,sync_page_token TEXT,sync_start_history_id TEXT,sync_generation TEXT)")
    db.execSQL("CREATE TABLE IF NOT EXISTS mail_labels(account_id TEXT NOT NULL REFERENCES mail_accounts(id) ON DELETE CASCADE,id TEXT NOT NULL,name TEXT NOT NULL,type TEXT NOT NULL,PRIMARY KEY(account_id,id))")
    db.execSQL("CREATE TABLE IF NOT EXISTS mail_threads(account_id TEXT NOT NULL REFERENCES mail_accounts(id) ON DELETE CASCADE,id TEXT NOT NULL,subject TEXT NOT NULL,snippet TEXT NOT NULL,last_message_at INTEGER NOT NULL,message_count INTEGER NOT NULL,in_inbox INTEGER NOT NULL DEFAULT 0,is_unread INTEGER NOT NULL DEFAULT 0,is_starred INTEGER NOT NULL DEFAULT 0,archived_locally INTEGER NOT NULL DEFAULT 0,read_later_locally INTEGER NOT NULL DEFAULT 0,sync_generation TEXT,PRIMARY KEY(account_id,id))")
    db.execSQL("CREATE INDEX IF NOT EXISTS mail_threads_date ON mail_threads(last_message_at DESC)")
    db.execSQL("CREATE INDEX IF NOT EXISTS mail_threads_account_inbox ON mail_threads(account_id,in_inbox,last_message_at DESC)")
    db.execSQL("CREATE INDEX IF NOT EXISTS mail_threads_account_unread ON mail_threads(account_id,is_unread,last_message_at DESC)")
    db.execSQL("CREATE TABLE IF NOT EXISTS mail_messages(account_id TEXT NOT NULL REFERENCES mail_accounts(id) ON DELETE CASCADE,id TEXT NOT NULL,thread_id TEXT NOT NULL,sender TEXT NOT NULL,recipients TEXT NOT NULL,subject TEXT NOT NULL,snippet TEXT NOT NULL,body TEXT NOT NULL,html_body TEXT,received_at INTEGER NOT NULL,label_ids TEXT NOT NULL,is_unread INTEGER NOT NULL DEFAULT 0,is_starred INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(account_id,id))")
    db.execSQL("CREATE INDEX IF NOT EXISTS mail_messages_thread ON mail_messages(account_id,thread_id,received_at)")
  },
)
