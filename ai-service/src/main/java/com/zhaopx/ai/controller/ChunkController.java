package com.zhaopx.ai.controller;

import com.zhaopx.ai.bean.res.R;
import com.zhaopx.ai.service.ChunkService;
import com.zhaopx.ai.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;

/**
 * @Description
 * @Author: ZhaoPengXiang
 * @Date: 2026-03-13 11:38
 */
@Slf4j
@RestController
@RequestMapping("/chunk")
public class ChunkController {

    @Autowired
    private ChunkService chunkService;
    @Autowired
    private DocumentService documentService;


    @PostMapping("/doChunk")
    public R<Object> doChunk(@RequestBody String text) {
        chunkService.doChunk(text);
        return R.success();
    }

    @GetMapping("/doReadDocument")
    public R<Object> doReadDocument(@RequestParam("path") String path) throws Exception {
        try {
            documentService.doReadDocument(path);
            return R.success();
        } catch (FileNotFoundException fe) {
            log.error("文件：{}，不存在", path);
            return R.fail(path + "文件不存在");
        } catch (Exception e) {
            log.error("系统异常：{}", e.getMessage(), e);
            return R.fail("其他错误");
        }
    }
}
