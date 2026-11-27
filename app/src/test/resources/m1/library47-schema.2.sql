CREATE TRIGGER reader_document_part_insert AFTER INSERT ON reader_document_parts BEGIN
 UPDATE reader_document_totals SET bytes=bytes+length(CAST(NEW.block_json AS BLOB)) WHERE id=1; END;
CREATE TRIGGER reader_documents_books_delete AFTER DELETE ON books BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE source_id='local' AND book_id=OLD.book_id; END;
CREATE TRIGGER reader_documents_books_insert AFTER INSERT ON books BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE source_id='local' AND book_id=NEW.book_id; END;
CREATE TRIGGER reader_documents_books_update AFTER UPDATE ON books BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE (source_id='local' AND book_id=NEW.book_id) OR (source_id='local' AND book_id=OLD.book_id); END;
CREATE TRIGGER reader_documents_bookshelf_delete AFTER DELETE ON bookshelf BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE source_id=OLD.source_id AND book_id=OLD.book_id; END;
CREATE TRIGGER reader_documents_bookshelf_insert BEFORE INSERT ON bookshelf BEGIN
 UPDATE reader_document_clock SET generation=generation+1 WHERE id=1;
 DELETE FROM reader_documents WHERE source_id=NEW.source_id AND book_id=NEW.book_id
 AND NEW.title IS NOT coalesce(
   (SELECT title FROM bookshelf WHERE source_id=NEW.source_id AND book_id=NEW.book_id),
   CASE WHEN NEW.source_id='local'
   THEN (SELECT json_extract(book_json,'$.title') FROM books WHERE book_id=NEW.book_id)
   ELSE (SELECT json_extract(book_json,'$.title') FROM source_books WHERE source_id=NEW.source_id AND book_id=NEW.book_id)
 END,'');
END;
CREATE TRIGGER reader_documents_bookshelf_update AFTER UPDATE ON bookshelf WHEN OLD.title IS NOT NEW.title BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE (source_id=NEW.source_id AND book_id=NEW.book_id) OR (source_id=OLD.source_id AND book_id=OLD.book_id); END;
CREATE TRIGGER reader_documents_chapter_cache_delete AFTER DELETE ON chapter_cache BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE source_id=OLD.source_id AND book_id=OLD.book_id AND chapter_index=OLD.chapter_index; END;
CREATE TRIGGER reader_documents_chapter_cache_insert AFTER INSERT ON chapter_cache BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE source_id=NEW.source_id AND book_id=NEW.book_id AND chapter_index=NEW.chapter_index; END;
CREATE TRIGGER reader_documents_chapter_cache_update AFTER UPDATE ON chapter_cache
WHEN CASE
 WHEN OLD.source_id IS NOT NEW.source_id OR OLD.book_id IS NOT NEW.book_id
   OR OLD.chapter_index IS NOT NEW.chapter_index OR OLD.content IS NOT NEW.content
   OR OLD.content_format_version IS NOT NEW.content_format_version
   OR OLD.title IS NOT NEW.title OR OLD.url IS NOT NEW.url THEN 1
 WHEN OLD.revision IS NEW.revision THEN 0
 WHEN OLD.revision IS NOT NULL OR NEW.source_id IS NOT 'local' THEN 1
 ELSE CASE WHEN EXISTS (
   SELECT 1 FROM reader_documents
   WHERE source_id=NEW.source_id AND book_id=NEW.book_id
     AND chapter_index=NEW.chapter_index AND processor_version=1
     AND CASE WHEN json_valid(document_json) THEN
       CASE WHEN json_type(document_json,'$.positionScope')='object'
         AND (SELECT count(*) FROM json_each(document_json) WHERE key='positionScope')=1 THEN
         CASE WHEN json_type(document_json,'$.positionScope.sourceId')='text'
           AND json_type(document_json,'$.positionScope.bookId')='text'
           AND json_type(document_json,'$.positionScope.chapterIndex')='integer'
           AND json_type(document_json,'$.positionScope.bodyVersion')='text'
           AND json_type(document_json,'$.positionScope.processingVersion')='text'
           AND (SELECT count(*) FROM json_each(document_json,'$.positionScope'))=5
           AND json_extract(document_json,'$.positionScope.sourceId')=NEW.source_id
           AND json_extract(document_json,'$.positionScope.bookId')=NEW.book_id
           AND json_extract(document_json,'$.positionScope.chapterIndex')=NEW.chapter_index
           AND json_extract(document_json,'$.positionScope.chapterIndex') BETWEEN 0 AND 4294967295
           AND length(json_extract(document_json,'$.positionScope.bodyVersion'))>0
           AND length(json_extract(document_json,'$.positionScope.processingVersion'))>0
           AND NEW.revision='reader-position-body-v1:'
             ||json_extract(document_json,'$.positionScope.bodyVersion')||':'
             ||json_extract(document_json,'$.positionScope.processingVersion')
         THEN 1 ELSE 0 END
       ELSE 0 END
     ELSE 0 END
 ) THEN 0 ELSE 1 END
END
BEGIN
 UPDATE reader_document_clock SET generation=generation+1 WHERE id=1;
 DELETE FROM reader_documents
 WHERE (source_id=NEW.source_id AND book_id=NEW.book_id AND chapter_index=NEW.chapter_index)
    OR (source_id=OLD.source_id AND book_id=OLD.book_id AND chapter_index=OLD.chapter_index);
END;
CREATE TRIGGER reader_documents_chinese_conversion_config_delete AFTER DELETE ON chinese_conversion_config BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE 1; END;
CREATE TRIGGER reader_documents_chinese_conversion_config_insert AFTER INSERT ON chinese_conversion_config BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE 1; END;
CREATE TRIGGER reader_documents_chinese_conversion_config_update AFTER UPDATE ON chinese_conversion_config BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE (1) OR (1); END;
CREATE TRIGGER reader_documents_replace_rules_delete AFTER DELETE ON replace_rules BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE 1; END;
CREATE TRIGGER reader_documents_replace_rules_insert AFTER INSERT ON replace_rules BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE 1; END;
CREATE TRIGGER reader_documents_replace_rules_update AFTER UPDATE ON replace_rules BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE (1) OR (1); END;
CREATE TRIGGER reader_documents_source_books_delete AFTER DELETE ON source_books BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE source_id=OLD.source_id AND book_id=OLD.book_id; END;
CREATE TRIGGER reader_documents_source_books_insert AFTER INSERT ON source_books BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE source_id=NEW.source_id AND book_id=NEW.book_id; END;
CREATE TRIGGER reader_documents_source_books_update AFTER UPDATE ON source_books WHEN json_extract(OLD.book_json,'$.title') IS NOT json_extract(NEW.book_json,'$.title') BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE (source_id=NEW.source_id AND book_id=NEW.book_id) OR (source_id=OLD.source_id AND book_id=OLD.book_id); END;
CREATE TRIGGER reader_documents_source_switch_transactions_delete AFTER DELETE ON source_switch_transactions BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE 1; END;
CREATE TRIGGER reader_documents_source_switch_transactions_insert AFTER INSERT ON source_switch_transactions BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE 1; END;
CREATE TRIGGER reader_documents_source_switch_transactions_update AFTER UPDATE ON source_switch_transactions BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE (1) OR (1); END;
CREATE TRIGGER reader_documents_sources_delete AFTER DELETE ON sources BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE source_id=OLD.source_id; END;
CREATE TRIGGER reader_documents_sources_insert AFTER INSERT ON sources BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE source_id=NEW.source_id; END;
CREATE TRIGGER reader_documents_sources_update AFTER UPDATE ON sources BEGIN UPDATE reader_document_clock SET generation=generation+1 WHERE id=1; DELETE FROM reader_documents WHERE (source_id=NEW.source_id) OR (source_id=OLD.source_id); END;
CREATE TRIGGER reader_documents_url_delete AFTER DELETE ON cache WHEN OLD.key LIKE 'reader-position-url-v1:%' BEGIN
 UPDATE reader_document_clock SET generation=generation+1 WHERE id=1;
 DELETE FROM reader_documents WHERE CASE WHEN json_valid(OLD.payload) THEN
 source_id=json_extract(OLD.payload,'$.sourceId') AND book_id=json_extract(OLD.payload,'$.bookId') AND chapter_index=json_extract(OLD.payload,'$.chapterIndex') ELSE 1 END; END;
CREATE TRIGGER reader_documents_url_insert AFTER INSERT ON cache WHEN NEW.key LIKE 'reader-position-url-v1:%' AND json_valid(NEW.payload) BEGIN
 UPDATE reader_document_clock SET generation=generation+1 WHERE id=1;
 DELETE FROM reader_documents WHERE source_id=json_extract(NEW.payload,'$.sourceId') AND book_id=json_extract(NEW.payload,'$.bookId') AND chapter_index=json_extract(NEW.payload,'$.chapterIndex'); END;
CREATE TRIGGER reader_documents_url_update AFTER UPDATE ON cache WHEN (OLD.key IS NOT NEW.key OR OLD.payload IS NOT NEW.payload) AND (OLD.key LIKE 'reader-position-url-v1:%' OR NEW.key LIKE 'reader-position-url-v1:%') BEGIN
 UPDATE reader_document_clock SET generation=generation+1 WHERE id=1;
 DELETE FROM reader_documents WHERE
 (OLD.key LIKE 'reader-position-url-v1:%' AND CASE WHEN json_valid(OLD.payload) THEN
 source_id=json_extract(OLD.payload,'$.sourceId') AND book_id=json_extract(OLD.payload,'$.bookId') AND chapter_index=json_extract(OLD.payload,'$.chapterIndex') ELSE 1 END)
 OR (NEW.key LIKE 'reader-position-url-v1:%' AND CASE WHEN json_valid(NEW.payload) THEN
 source_id=json_extract(NEW.payload,'$.sourceId') AND book_id=json_extract(NEW.payload,'$.bookId') AND chapter_index=json_extract(NEW.payload,'$.chapterIndex') ELSE 1 END); END;
CREATE TRIGGER reader_local_scalar_catalog_DELETE AFTER DELETE ON reader_catalog_index_meta BEGIN DELETE FROM cache WHERE substr(OLD.toc_key,1,18)='local_book.v1.toc:' AND key='local_book.scalar-facts.v1:'||substr(OLD.toc_key,19); END;
CREATE TRIGGER reader_local_scalar_catalog_INSERT AFTER INSERT ON reader_catalog_index_meta BEGIN DELETE FROM cache WHERE substr(NEW.toc_key,1,18)='local_book.v1.toc:' AND key='local_book.scalar-facts.v1:'||substr(NEW.toc_key,19); END;
CREATE TRIGGER reader_local_scalar_catalog_UPDATE AFTER UPDATE ON reader_catalog_index_meta BEGIN DELETE FROM cache WHERE substr(OLD.toc_key,1,18)='local_book.v1.toc:' AND key='local_book.scalar-facts.v1:'||substr(OLD.toc_key,19);DELETE FROM cache WHERE substr(NEW.toc_key,1,18)='local_book.v1.toc:' AND key='local_book.scalar-facts.v1:'||substr(NEW.toc_key,19); END;
CREATE TRIGGER reader_local_scalar_generation AFTER UPDATE OF generation ON reader_document_clock
        WHEN OLD.generation IS NOT NEW.generation BEGIN DELETE FROM cache WHERE key>='local_book.scalar-facts.v1:' AND key<'local_book.scalar-facts.v1;'; END;
CREATE TRIGGER reader_local_scalar_shelf_DELETE AFTER DELETE ON cache WHEN OLD.key>='local_book.scalar-facts.v1:' AND OLD.key<'local_book.scalar-facts.v1;'
            BEGIN UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1; END;
CREATE TRIGGER reader_local_scalar_shelf_INSERT AFTER INSERT ON cache WHEN NEW.key>='local_book.scalar-facts.v1:' AND NEW.key<'local_book.scalar-facts.v1;'
            BEGIN UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1; END;
CREATE TRIGGER reader_local_scalar_shelf_UPDATE AFTER UPDATE ON cache WHEN (OLD.key>='local_book.scalar-facts.v1:' AND OLD.key<'local_book.scalar-facts.v1;') OR (NEW.key>='local_book.scalar-facts.v1:' AND NEW.key<'local_book.scalar-facts.v1;')
            BEGIN UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1; END;
CREATE TRIGGER reader_shelf_clock_bookshelf_DELETE
                AFTER DELETE ON bookshelf BEGIN
                UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
                END;
CREATE TRIGGER reader_shelf_clock_bookshelf_INSERT
                AFTER INSERT ON bookshelf BEGIN
                UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
                END;
CREATE TRIGGER reader_shelf_clock_bookshelf_UPDATE
                AFTER UPDATE ON bookshelf BEGIN
                UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
                END;
CREATE TRIGGER reader_shelf_clock_cache_DELETE
            AFTER DELETE ON cache WHEN OLD.key LIKE 'bookshelf.catalog.v1:%' BEGIN
            UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
            END;
CREATE TRIGGER reader_shelf_clock_cache_INSERT
            AFTER INSERT ON cache WHEN NEW.key LIKE 'bookshelf.catalog.v1:%' BEGIN
            UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
            END;
CREATE TRIGGER reader_shelf_clock_cache_UPDATE
            AFTER UPDATE ON cache WHEN OLD.key LIKE 'bookshelf.catalog.v1:%' OR NEW.key LIKE 'bookshelf.catalog.v1:%' BEGIN
            UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
            END;
CREATE TRIGGER reader_shelf_clock_manga_admissions_DELETE
AFTER DELETE ON manga_admissions BEGIN
UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
END;
CREATE TRIGGER reader_shelf_clock_manga_admissions_INSERT
AFTER INSERT ON manga_admissions BEGIN
UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
END;
CREATE TRIGGER reader_shelf_clock_manga_admissions_UPDATE
AFTER UPDATE ON manga_admissions BEGIN
UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
END;
CREATE TRIGGER reader_shelf_clock_reader_catalog_index_meta_DELETE
                AFTER DELETE ON reader_catalog_index_meta BEGIN
                UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
                END;
CREATE TRIGGER reader_shelf_clock_reader_catalog_index_meta_INSERT
                AFTER INSERT ON reader_catalog_index_meta BEGIN
                UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
                END;
CREATE TRIGGER reader_shelf_clock_reader_catalog_index_meta_UPDATE
                AFTER UPDATE ON reader_catalog_index_meta BEGIN
                UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
                END;
CREATE TRIGGER reader_shelf_clock_reading_progress_DELETE
                AFTER DELETE ON reading_progress BEGIN
                UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
                END;
CREATE TRIGGER reader_shelf_clock_reading_progress_INSERT
                AFTER INSERT ON reading_progress BEGIN
                UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
                END;
CREATE TRIGGER reader_shelf_clock_reading_progress_UPDATE
                AFTER UPDATE ON reading_progress BEGIN
                UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
                END;
CREATE TRIGGER reader_shelf_clock_sources_DELETE
                AFTER DELETE ON sources BEGIN
                UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
                END;
CREATE TRIGGER reader_shelf_clock_sources_INSERT
                AFTER INSERT ON sources BEGIN
                UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
                END;
CREATE TRIGGER reader_shelf_clock_sources_UPDATE
                AFTER UPDATE ON sources BEGIN
                UPDATE reader_shelf_projection_clock SET revision=revision+1 WHERE singleton=1;
                END;
CREATE TRIGGER search_alias_delete AFTER DELETE ON search_books BEGIN
 DELETE FROM search_book_aliases WHERE origin=OLD.origin AND book_url=OLD.book_url;
 UPDATE search_relation_state SET revision=revision+1 WHERE singleton=1;
END;
CREATE TRIGGER search_alias_insert AFTER INSERT ON search_books BEGIN
 DELETE FROM search_book_aliases WHERE origin=NEW.origin AND book_url=NEW.book_url;
 INSERT INTO search_book_aliases SELECT NEW.origin,NEW.book_url,value FROM
 json_each(reader_search_aliases_v2(NEW.name,NEW.author,NEW.acquisition_json,
 (SELECT policy_json FROM search_source_author_policy WHERE origin=NEW.origin)));
 UPDATE search_relation_state SET revision=revision+1 WHERE singleton=1;
END;
CREATE TRIGGER search_alias_update AFTER UPDATE ON search_books BEGIN
 DELETE FROM search_book_aliases WHERE origin=OLD.origin AND book_url=OLD.book_url;
 DELETE FROM search_book_aliases WHERE origin=NEW.origin AND book_url=NEW.book_url;
 INSERT INTO search_book_aliases SELECT NEW.origin,NEW.book_url,value FROM
 json_each(reader_search_aliases_v2(NEW.name,NEW.author,NEW.acquisition_json,
 (SELECT policy_json FROM search_source_author_policy WHERE origin=NEW.origin)));
 UPDATE search_relation_state SET revision=revision+1 WHERE singleton=1;
END;
CREATE TRIGGER search_source_delete AFTER DELETE ON sources BEGIN
 DELETE FROM search_source_author_policy WHERE origin=OLD.source_id;
 DELETE FROM search_book_aliases WHERE origin=OLD.source_id;
 INSERT INTO search_book_aliases SELECT b.origin,b.book_url,j.value FROM search_books b,
 json_each(reader_search_aliases_v2(b.name,b.author,b.acquisition_json,NULL)) j
 WHERE b.origin=OLD.source_id;
 UPDATE search_relation_state SET revision=revision+1 WHERE singleton=1;
END;
CREATE TRIGGER search_source_insert AFTER INSERT ON sources BEGIN
 INSERT OR REPLACE INTO search_source_author_policy VALUES(NEW.source_id,reader_search_source_policy_v1(NEW.source_json));
 DELETE FROM search_book_aliases WHERE origin=NEW.source_id;
 INSERT INTO search_book_aliases SELECT b.origin,b.book_url,j.value FROM search_books b
 JOIN search_source_author_policy p ON p.origin=b.origin,
 json_each(reader_search_aliases_v2(b.name,b.author,b.acquisition_json,p.policy_json)) j
 WHERE b.origin=NEW.source_id;
 UPDATE search_relation_state SET revision=revision+1 WHERE singleton=1;
END;
CREATE TRIGGER search_source_update AFTER UPDATE ON sources BEGIN
 DELETE FROM search_source_author_policy WHERE origin=OLD.source_id;
 INSERT OR REPLACE INTO search_source_author_policy VALUES(NEW.source_id,reader_search_source_policy_v1(NEW.source_json));
 DELETE FROM search_book_aliases WHERE origin IN (OLD.source_id,NEW.source_id);
 INSERT INTO search_book_aliases SELECT b.origin,b.book_url,j.value FROM search_books b
 LEFT JOIN search_source_author_policy p ON p.origin=b.origin,
 json_each(reader_search_aliases_v2(b.name,b.author,b.acquisition_json,p.policy_json)) j
 WHERE b.origin IN (OLD.source_id,NEW.source_id);
 UPDATE search_relation_state SET revision=revision+1 WHERE singleton=1;
END;
CREATE TRIGGER source_supply_delete AFTER DELETE ON sources BEGIN
 INSERT OR IGNORE INTO source_supply_deletions(source_id) VALUES(OLD.source_id);
 UPDATE source_supply_bundles SET complete=0 WHERE bundle_id IN (SELECT bundle_id FROM source_supply_items WHERE source_id=OLD.source_id);
 UPDATE source_supply_items SET dirty=1,suppressed=1 WHERE source_id=OLD.source_id;
END;
CREATE TRIGGER source_supply_insert AFTER INSERT ON sources BEGIN
 DELETE FROM source_supply_deletions WHERE source_id=NEW.source_id;
 UPDATE source_supply_bundles SET complete=0 WHERE bundle_id IN (SELECT bundle_id FROM source_supply_items WHERE source_id=NEW.source_id);
 UPDATE source_supply_items SET dirty=1,suppressed=0 WHERE source_id=NEW.source_id;
END;
CREATE TRIGGER source_supply_update AFTER UPDATE ON sources BEGIN
 UPDATE source_supply_bundles SET complete=0 WHERE bundle_id IN (SELECT bundle_id FROM source_supply_items WHERE source_id=NEW.source_id);
 UPDATE source_supply_items SET dirty=1,suppressed=0 WHERE source_id=NEW.source_id;
END;
INSERT INTO library_identity_journals(journal_id,payload)
 VALUES('synthetic-preserve-journal','{"unmodeled":true,"proof":"fixture"}');
INSERT INTO reader_shelf_projection_clock(singleton,epoch,revision)
 VALUES(1,lower(hex(randomblob(16))),0);
INSERT INTO manga_runtime_epoch(singleton,value) VALUES(1,0);
INSERT INTO manga_session_runtime(singleton,value,process_identity,require_release_token) VALUES(1,0,0,0);
INSERT INTO search_relation_state(singleton,revision,epoch,normalization_version)
 VALUES(1,1,'00000000000000000000000000000000','synthetic-stale-normalization');
PRAGMA user_version=47;
