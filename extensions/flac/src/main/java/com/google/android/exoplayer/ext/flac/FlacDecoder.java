/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer.ext.flac;

import com.google.android.exoplayer.DecoderInputBuffer;
import com.google.android.exoplayer.util.FlacStreamInfo;
import com.google.android.exoplayer.util.extensions.SimpleDecoder;
import com.google.android.exoplayer.util.extensions.SimpleOutputBuffer;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Flac decoder.
 */
/* package */ final class FlacDecoder extends
    SimpleDecoder<DecoderInputBuffer, SimpleOutputBuffer, FlacDecoderException> {

  private final int maxOutputBufferSize;
  private final FlacJni decoder;

  /**
   * Creates a Flac decoder.
   *
   * @param numInputBuffers The number of input buffers.
   * @param numOutputBuffers The number of output buffers.
   * @param initializationData Codec-specific initialization data. It should contain only one entry
   *    which is the flac file header.
   * @throws FlacDecoderException Thrown if an exception occurs when initializing the decoder.
   */
  public FlacDecoder(int numInputBuffers, int numOutputBuffers, List<byte[]> initializationData)
      throws FlacDecoderException {
    super(new DecoderInputBuffer[numInputBuffers], new SimpleOutputBuffer[numOutputBuffers]);
    if (initializationData.size() != 1) {
      throw new FlacDecoderException("Wrong number of initialization data");
    }

    decoder = new FlacJni();

    ByteBuffer metadata = ByteBuffer.wrap(initializationData.get(0));
    decoder.setData(metadata);
    FlacStreamInfo streamInfo = decoder.decodeMetadata();
    if (streamInfo == null) {
      throw new FlacDecoderException("Metadata decoding failed");
    }

    setInitialInputBufferSize(streamInfo.maxFrameSize);
    maxOutputBufferSize = streamInfo.maxDecodedFrameSize();
  }

  @Override
  public DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
  }

  @Override
  public SimpleOutputBuffer createOutputBuffer() {
    return new SimpleOutputBuffer(this);
  }

  @Override
  public FlacDecoderException decode(DecoderInputBuffer inputBuffer,
      SimpleOutputBuffer outputBuffer, boolean reset) {
    if (reset) {
      decoder.flush();
    }
    ByteBuffer data = inputBuffer.data;
    outputBuffer.timestampUs = inputBuffer.timeUs;
    data.limit(data.position());
    data.position(data.position() - inputBuffer.size);
    outputBuffer.init(maxOutputBufferSize);
    decoder.setData(data);
    int result = decoder.decodeSample(outputBuffer.data);
    if (result < 0) {
      return new FlacDecoderException("Frame decoding failed");
    }
    outputBuffer.data.position(0);
    outputBuffer.data.limit(result);
    return null;
  }

  @Override
  public void release() {
    super.release();
    decoder.release();
  }

}
