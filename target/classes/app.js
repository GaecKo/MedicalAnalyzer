/**
 * Copyright (c) 2022, Sebastien Jodogne, ICTEAM UCLouvain, Belgium
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 **/


var dicomImage = document.createElement('canvas');

function Draw(ctx) {
  if (dicomImage.width > 0 &&
      dicomImage.height > 0) {
    ctx.drawImage(dicomImage, 0, 0);
  }
}

function GetExtent() {
  return BestRendering.CreateExtent(0, 0, dicomImage.width, dicomImage.height);
}

function OnUploadedDicom() {
  axios.get('get-range').then(function (response) {
    var json = response.data;
    document.getElementById('hounsfield-low').value = json.low;
    document.getElementById('hounsfield-high').value = json.high;  
    ApplyHounsfield(true);
  });
}

function ApplyHounsfield(fitScene) {
  var low = document.getElementById('hounsfield-low').value;
  var high = document.getElementById('hounsfield-high').value;

  document.getElementById('hounsfield-low-span').innerHTML = low;
  document.getElementById('hounsfield-high-span').innerHTML = high;

  axios.post('apply-hounsfield', {
    low: low,
    high: high
  }, {
    responseType: 'arraybuffer'
  }).then(function (response) {
    BestRendering.LoadImageFromBackendIntoCanvas(dicomImage, response.data);

    if (fitScene) {
      BestRendering.FitContainer('display');
    } else {
      BestRendering.DrawContainer('display');
    }
  });
}

function SetWindowing(center, width) {
  var low = center - width / 2.0;
  var high = center + width / 2.0;
  document.getElementById('hounsfield-low').value = low;
  document.getElementById('hounsfield-high').value = high;
  ApplyHounsfield(false);
}

BestRendering.InitializeContainer('display', Draw, GetExtent);
BestRendering.InstallFileUploader('dicom-input', '/upload-source', 'data', OnUploadedDicom);

document.getElementById('hounsfield-low').addEventListener('change', function(event) {
  ApplyHounsfield(false);
});

document.getElementById('hounsfield-high').addEventListener('change', function(event) {
  ApplyHounsfield(false);
});

document.getElementById('bone').addEventListener('click', function(event) {
  SetWindowing(300, 2000);
});

document.getElementById('lung').addEventListener('click', function(event) {
  SetWindowing(-600, 1600);
});
