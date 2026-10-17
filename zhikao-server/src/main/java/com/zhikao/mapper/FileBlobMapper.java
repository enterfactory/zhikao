package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.FileBlob;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FileBlobMapper extends BaseMapper<FileBlob> {
    // Lock the content identity even before its first INSERT, across JVMs.
    @Select("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(#{hash},0))")
    int lockContent(@Param("hash") String hash);

    @Select("""
        SELECT (SELECT COUNT(*) FROM sys_user WHERE avatar=CONCAT('/api/file/view/',#{id}))
        + (SELECT COUNT(*) FROM question WHERE CONCAT_WS(' ',content,options,answer,analysis)
            ~ CONCAT('/api/file/view/',#{id},'([^0-9]|$)'))
        + (SELECT COUNT(*) FROM paper_question WHERE CONCAT_WS(' ',content,options,answer,analysis)
            ~ CONCAT('/api/file/view/',#{id},'([^0-9]|$)'))
        + (SELECT COUNT(*) FROM crawl_question WHERE CONCAT_WS(' ',content,options,answer,analysis)
            ~ CONCAT('/api/file/view/',#{id},'([^0-9]|$)'))
        + (SELECT COUNT(*) FROM practice_item WHERE CONCAT_WS(' ',content,options,standard_answer,analysis)
            ~ CONCAT('/api/file/view/',#{id},'([^0-9]|$)'))
        """)
    long businessReferences(@Param("id") Long id);
}
