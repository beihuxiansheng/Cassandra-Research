/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.cassandra.io.util;

import com.google.common.util.concurrent.RateLimiter;

import org.apache.cassandra.io.compress.CompressedRandomAccessReader;
import org.apache.cassandra.io.compress.CompressedSequentialWriter;
import org.apache.cassandra.io.compress.CompressedThrottledReader;
import org.apache.cassandra.io.compress.CompressionMetadata;

//与CompressedSegmentedFile类似，只不过有缓存
public class CompressedPoolingSegmentedFile extends PoolingSegmentedFile implements ICompressedFile
{
    public final CompressionMetadata metadata;

    public CompressedPoolingSegmentedFile(ChannelProxy channel, CompressionMetadata metadata)
    {
        super(new Cleanup(channel, metadata), channel, metadata.dataLength, metadata.compressedFileLength);
        this.metadata = metadata;
    }

    private CompressedPoolingSegmentedFile(CompressedPoolingSegmentedFile copy)
    {
        super(copy);
        this.metadata = copy.metadata;
    }

    protected static final class Cleanup extends PoolingSegmentedFile.Cleanup
    {
        final CompressionMetadata metadata;
        protected Cleanup(ChannelProxy channel, CompressionMetadata metadata)
        {
            super(channel);
            this.metadata = metadata;
        }
        public void tidy()
        {
            super.tidy();
            metadata.close();
        }
    }

    public static class Builder extends CompressedSegmentedFile.Builder
    {
        public Builder(CompressedSequentialWriter writer)
        {
            super(writer);
        }

        public void addPotentialBoundary(long boundary)
        {
            // only one segment in a standard-io file
        }

        public SegmentedFile complete(ChannelProxy channel, long overrideLength, boolean isFinal)
        {
            return new CompressedPoolingSegmentedFile(channel, metadata(channel.filePath(), overrideLength, isFinal));
        }
    }

    public void dropPageCache(long before)
    {
        if (before >= metadata.dataLength)
            super.dropPageCache(0);
        super.dropPageCache(metadata.chunkFor(before).offset);
    }

    public RandomAccessReader createReader()
    {
        return CompressedRandomAccessReader.open(channel, metadata, null);
    }

    public RandomAccessReader createThrottledReader(RateLimiter limiter)
    {
        return CompressedThrottledReader.open(channel, metadata, limiter);
    }

    protected RandomAccessReader createPooledReader()
    {
        return CompressedRandomAccessReader.open(channel, metadata, this);
    }

    public CompressionMetadata getMetadata()
    {
        return metadata;
    }

    public CompressedPoolingSegmentedFile sharedCopy()
    {
        return new CompressedPoolingSegmentedFile(this);
    }
}
