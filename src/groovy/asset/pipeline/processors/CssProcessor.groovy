/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package asset.pipeline.processors

import asset.pipeline.*
import java.net.URL
import java.net.URI
import org.apache.commons.codec.net.URLCodec

/**
* This Processor iterates over relative image paths in a CSS file and
* recalculates their path relative to the base file. In precompiler mode
* the image urls are also cache digested.
* @author David Estes
*/
class CssProcessor extends AbstractProcessor {

    CssProcessor(AssetCompiler precompiler) {
        super(precompiler)
    }

    def process(inputText, assetFile) {
        def cachedPaths = [:]
        return inputText.replaceAll(/url\([\'\"]?([a-zA-Z0-9\-\_\.\/\@\#\?\ \&\+\%\=]+)[\'\"]?\)/) { fullMatch, assetPath ->
            def replacementPath = assetPath.trim()
            if(cachedPaths[assetPath]) {
                replacementPath = cachedPaths[assetPath]
            } else if(isRelativePath(assetPath)) {
                def urlRep = new URL("http://hostname/${assetPath}") //Split out subcomponents
                def relativeFileName = [assetFile.parentPath,urlRep.path].join("/")
                def encodedFileName = new URLCodec().encode(relativeFileName)
                def normalizedFileName = new URI(encodedFileName).normalize().getPath()
                normalizedFileName = new URLCodec().decode(normalizedFileName)
                println "Looking for Relative File ${relativeFileName}"
                println "Looking for NormalizedFileName ${normalizedFileName}"
                def cssFile = AssetHelper.fileForFullName(relativeFileName)
                if(!cssFile) {
                    cssFile = AssetHelper.fileForFullName(normalizedFileName)
                }
                if(cssFile) {
                    replacementPath = relativePathToBaseFile(cssFile, assetFile.baseFile ?: assetFile, this.precompiler ? true : false)
                    println "ReplaceMent Path = ${replacementPath}"
                    if(urlRep.query != null) {
                        replacementPath += "?${urlRep.query}"
                    }
                    if(urlRep.ref) {
                        replacementPath += "#${urlRep.ref}"
                    }
                    cachedPaths[assetPath] = replacementPath
                }
            }
            return "url('${replacementPath}')"
        }

    }

    private isRelativePath(assetPath) {
        return !assetPath.startsWith("/") && !assetPath.startsWith("http")
    }

    private relativePathToBaseFile(file, baseFile, useDigest=false) {
        def baseRelativePath = baseFile.parentPath ? baseFile.parentPath.split(AssetHelper.DIRECTIVE_FILE_SEPARATOR).findAll{it}.reverse() : []
        def currentRelativePath = file.parentPath ? file.parentPath.split(AssetHelper.DIRECTIVE_FILE_SEPARATOR).findAll({it}).reverse() : []
        println "Checking Base Path ${baseRelativePath} against ${currentRelativePath}"
        def filePathIndex=currentRelativePath.size()- 1
        def baseFileIndex=baseRelativePath.size() - 1

        while(filePathIndex > 0 && baseFileIndex > 0 && baseRelativePath[baseFileIndex] == currentRelativePath[filePathIndex]) {
            filePathIndex--
            baseFileIndex--
        }

        def calculatedPath = []

        // for each remaining level in the home path, add a ..
        for(;baseFileIndex>=0;baseFileIndex--) {
            calculatedPath << ".."
        }

        for(;filePathIndex>=0;filePathIndex--) {
            calculatedPath << currentRelativePath[filePathIndex]
        }
        if(useDigest) {
            def extension = AssetHelper.extensionFromURI(file.getName())
            def fileName  = AssetHelper.nameWithoutExtension(file.getName())
            // def assetFile = AssetHelper.assetForFile(file)
            def digestName
            if(!(file instanceof GenericAssetFile)) {
                def directiveProcessor = new DirectiveProcessor(assetFile.contentType, precompiler)
                def fileData   = directiveProcessor.compile(file)
                digestName = AssetHelper.getByteDigest(fileData.bytes)
            }
            else {
                digestName = AssetHelper.getByteDigest(file.bytes)
            }
            calculatedPath << "${fileName}-${digestName}.${extension}"
        } else {
            calculatedPath << file.getName()
        }

        return calculatedPath.join(AssetHelper.DIRECTIVE_FILE_SEPARATOR)
    }

    private relativePath(file, includeFileName=false) {
        def path
        if(includeFileName) {
            path = file.class.name == 'java.io.File' ? file.getCanonicalPath().split(AssetHelper.QUOTED_FILE_SEPARATOR) : file.file.getCanonicalPath().split(AssetHelper.QUOTED_FILE_SEPARATOR)
        } else {
            path = file.class.name == 'java.io.File' ? new File(file.getParent()).getCanonicalPath().split(AssetHelper.QUOTED_FILE_SEPARATOR) : new File(file.file.getParent()).getCanonicalPath().split(AssetHelper.QUOTED_FILE_SEPARATOR)
        }

        def startPosition = path.findLastIndexOf{ it == "grails-app" }
        if(startPosition == -1) {
            startPosition = path.findLastIndexOf{ it == 'web-app' }
            if(startPosition+2 >= path.length) {
                return ""
            }
            path = path[(startPosition+2)..-1]
        }
        else {
            if(startPosition+3 >= path.length) {
                return ""
            }
            path = path[(startPosition+3)..-1]
        }

        return path.join(AssetHelper.DIRECTIVE_FILE_SEPARATOR)
    }
}
