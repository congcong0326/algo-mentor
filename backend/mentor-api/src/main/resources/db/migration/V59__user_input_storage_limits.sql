ALTER TABLE user_problem_note
  ADD CONSTRAINT ck_user_problem_note_outline_storage_bytes
      CHECK (octet_length(outline_json::TEXT) <= 65536),
  ADD CONSTRAINT ck_user_problem_note_outline_text_fields
      CHECK (
        COALESCE(char_length(outline_json ->> 'coreIdea'), 0) <= 10000
        AND COALESCE(char_length(outline_json ->> 'dataStructureNotes'), 0) <= 10000
        AND COALESCE(char_length(outline_json ->> 'algorithmNotes'), 0) <= 10000
        AND COALESCE(char_length(outline_json ->> 'edgeCases'), 0) <= 10000
        AND COALESCE(char_length(outline_json #>> '{timeComplexity,customText}'), 0) <= 10000
        AND COALESCE(char_length(outline_json #>> '{spaceComplexity,customText}'), 0) <= 10000
      );

ALTER TABLE learning_plan_draft
  ADD CONSTRAINT ck_learning_plan_draft_command_storage_bytes
      CHECK (octet_length(command_json::TEXT) <= 65536);
