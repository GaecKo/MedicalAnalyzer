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


/**
 * Here is the convention we use to define the library:
 * https://stackoverflow.com/a/881611
 **/

var BestRendering = new function() {
  
  /*****************************************************************
   ** PRIVATE FUNCTIONS
   *****************************************************************/

  var CreateTransform = function(t11, t12, t13,
                                 t21, t22, t23) {
    // "DOMMatrix" uses a transposed matrix
    return new DOMMatrix([ t11, t21,
                           t12, t22,
                           t13, t23 ]);
  };

  var CreateIdentityTransform = function() {
    return CreateTransform(1, 0, 0,
                           0, 1, 0);
  };

  var GetCanvas = function(container) {
    var canvas = container.getElementsByTagName('canvas');
    if (canvas.length == 0) {
      throw 'Container without canvas';
    } else if (canvas.length > 1) {
      throw 'Container with more than one canvas';
    } else {
      return canvas[0];
    }
  };

  var GetContainerById = function(containerId) {
    var container = document.getElementById(containerId);
    if (container == null) {
      throw 'Unknown container: ' + containerId;
    } else {
      return container;
    }
  };
  
  var FitCanvas = function(container, canvas) {
    if ('properties' in canvas &&
        typeof canvas.properties.getExtentCallback === 'function') {
      var extent = canvas.properties.getExtentCallback();
      if (extent === null) {
        canvas.properties.transform = CreateIdentityTransform();
      } else {
        var width = Math.abs(extent.x1 - extent.x2);
        var height = Math.abs(extent.y1 - extent.y2);
        if (width <= Number.EPSILON ||
            height <= Number.EPSILON) {
          canvas.properties.transform = CreateIdentityTransform();
        } else {
          var sceneCenterToOrigin = CreateTransform(1, 0, -(extent.x1 + extent.x2) / 2.0,
                                                    0, 1, -(extent.y1 + extent.y2) / 2.0);
          var scaling = Math.min(container.offsetWidth / width,
                                 container.offsetHeight / height);
          var scaleAtOrigin = CreateTransform(scaling, 0, 0, 
                                              0, scaling, 0);
          canvas.properties.transform = scaleAtOrigin.multiply(sceneCenterToOrigin);
        }      
      }

      DrawCanvas(container, canvas);
    }
  };

  var DrawCanvas = function(container, canvas) {
    if ('properties' in canvas) {
      var ctx = canvas.getContext('2d');
      ctx.canvas.width = container.offsetWidth;
      ctx.canvas.height = container.offsetHeight; 

      var viewportToCanvas = CreateTransform(1, 0, container.offsetWidth / 2.0,
                                             0, 1, container.offsetHeight / 2.0);
      ctx.setTransform(viewportToCanvas.multiply(canvas.properties.transform));

      ctx.lineWidth = 1.0 / ctx.getTransform().a;
      
      canvas.properties.drawCallback(ctx);
    }
  };

  

  /*****************************************************************
   ** PUBLIC FUNCTIONS
   *****************************************************************/

  /**
   * Return a 2D extent, to be used as a return value for the
   * "getExtentCallback()" of a container. The extent must be provided
   * as the coordinates of the two extreme points (x1,y1) and (x2,y2).
   **/
  this.CreateExtent = function(x1, y1, x2, y2) {
    return {
      x1: x1,
      y1: y1,
      x2: x2,
      y2: y2
    }
  };


  /**
   * Redraw all the containers that are managed by the BestRendering
   * library.
   *
   * Usage in the LSINC1114 course: You should not have to call this
   * function directly.
   **/
  this.DrawAll = function() {
    var containers = document.getElementsByClassName('container');

    for (var i = 0; i < containers.length; i++) {
      DrawCanvas(containers[i], GetCanvas(containers[i]));
    }
  };


  /**
   * Fit the viewport of all the containers that are managed by the
   * BestRendering library, to the extent of their content.
   *
   * Usage in the LSINC1114 course: You should not have to call this
   * function directly.
   **/
  this.FitAll = function() {
    var containers = document.getElementsByClassName('container');

    for (var i = 0; i < containers.length; i++) {
      FitCanvas(containers[i], GetCanvas(containers[i]));
    }
  };


  /**
   * Redraw the canvas in the <div> container whose ID in the DOM is
   * given by argument "containerId". This will call the
   * "drawCallback(ctx)" function associated with the container.
   **/
  this.DrawContainer = function(containerId) {
    var container = GetContainerById(containerId);
    var canvas = GetCanvas(container);
    DrawCanvas(container, canvas);
  };


  /**
   * Fit the viewport of the canvas in the <div> container whose ID in
   * the DOM is given by argument "containerId", to the extent of its
   * content. This will first call the "getExtentCallback()" function
   * associated with the container, then its "drawCallback(ctx)".
   **/
  this.FitContainer = function(containerId) {
    var container = GetContainerById(containerId);
    var canvas = GetCanvas(container);
    FitCanvas(container, canvas);
  };


  /**
   * Initialize the <div> container whose ID in the DOM is given by
   * argument "containerId", so that it gets managed by the
   * BestRendering library. The <div> must contain a single <canvas>
   * element, and must be tagged with class "container". Handlers for
   * the mouse events will be automatically installed on the canvas.
   *
   * The "drawCallback(ctx)" function is invoked whenever the canvas
   * must be redrawn. This callback is provided with the "ctx" drawing
   * context, which corresponds to a JavaScript object of class
   * "CanvasRenderingContext2D". The BestRendering library
   * automatically sets the 2D transform to reflect zoom/pan done by
   * the mouse interactions.
   *
   * The "getExtentCallback()" function is invoked whenever the
   * BestRendering library must fit the viewport of the canvas to the
   * extent of its content.
   **/
  this.InitializeContainer = function(containerId, drawCallback, getExtentCallback) {
    if (typeof drawCallback !== 'function') {
      throw 'Drawing function expected';
    } else {    
      var container = GetContainerById(containerId);
      var canvas = GetCanvas(container);
      
      canvas.properties = {
        transform: CreateIdentityTransform(),
        drawCallback: drawCallback,
        getExtentCallback: getExtentCallback
      };

      DrawCanvas(container, canvas);
      
      container.addEventListener('contextmenu', function(event) {
        // Disable right click on the canvas
        event.preventDefault();
      });
      
      container.addEventListener('mousedown', function(event) {
        var type = null;
        if (event.button == 0 ||
            event.button == 1) {
          type = 'offset';
        } else if (event.button == 2) {
          type = 'zoom';
        }
        
        if (type !== null) {
          var clickViewport = new DOMPoint(event.offsetX - container.offsetWidth / 2.0,
                                           event.offsetY - container.offsetHeight / 2.0);
          var clickScene = canvas.properties.transform.inverse().transformPoint(clickViewport);

          window.activeMouseTracker = {
            type: type,
            canvas: canvas,
            clickCanvasX: event.offsetX,
            clickCanvasY: event.offsetY,
            clickSceneX: clickScene.x,
            clickSceneY: clickScene.y,
            clickViewportX: clickViewport.x,
            clickViewportY: clickViewport.y,
            initialTransform: canvas.properties.transform
          };

          event.preventDefault();
        }      
      });

      container.addEventListener('mousemove', function(event) {
        if (typeof window.activeMouseTracker === 'object' &&
            event.target == window.activeMouseTracker.canvas) {

          if (window.activeMouseTracker.type == 'offset') {
            var offset = CreateTransform(1, 0, event.offsetX - window.activeMouseTracker.clickCanvasX,
                                         0, 1, event.offsetY - window.activeMouseTracker.clickCanvasY);
            canvas.properties.transform = offset.multiply(window.activeMouseTracker.initialTransform);
            DrawCanvas(container, canvas);
          }
          
          if (window.activeMouseTracker.type == 'zoom' &&
              container.offsetHeight >= 3) {
            var MIN_ZOOM = -4;
            var MAX_ZOOM = 4;
            
            var dy = (event.offsetY - window.activeMouseTracker.clickCanvasY) / (container.offsetHeight - 1);

            // Linear interpolation from [-1, 1] to [MIN_ZOOM, MAX_ZOOM]
            var zoom;
            if (dy < -1) {
              zoom = MIN_ZOOM;
            } else if (dy > 1) {
              zoom = MAX_ZOOM;
            } else {
              zoom = MIN_ZOOM + (MAX_ZOOM - MIN_ZOOM) * (dy + 1.0) / 2.0;
            }

            var scaling = Math.pow(2.0, zoom);
            var t1 = CreateTransform(scaling, 0, 0,
                                     0, scaling, 0);
            t1 = t1.multiply(window.activeMouseTracker.initialTransform);

            // Keep the clicked scene point at the same location in viewport coordinates
            var fixedPoint = t1.transformPoint(new DOMPoint(window.activeMouseTracker.clickSceneX,
                                                            window.activeMouseTracker.clickSceneY));
            var t2 = CreateTransform(1, 0, window.activeMouseTracker.clickViewportX - fixedPoint.x,
                                     0, 1, window.activeMouseTracker.clickViewportY - fixedPoint.y);
            canvas.properties.transform = t2.multiply(t1);
            
            DrawCanvas(container, canvas);
          }
        }
      });

      container.addEventListener('mouseup', function(event) {
        window.activeMouseTracker = {};
      });
    }
  };


  /**
   * This function disables the antialiasing of images in the given
   * drawing context, if called from within some "drawCallback(ctx)"
   * function.
   *
   * Usage in the LSINC1114 course: Using this function is optional.
   **/
  this.DisableAntialiasing = function(ctx) {
    // https://stackoverflow.com/a/32798277
    ctx['imageSmoothingEnabled'] = false;       /* standard */
    //ctx['mozImageSmoothingEnabled'] = false;    /* Older Firefox */
    ctx['oImageSmoothingEnabled'] = false;      /* Opera */
    ctx['webkitImageSmoothingEnabled'] = false; /* Safari */
    ctx['msImageSmoothingEnabled'] = false;     /* IE */
  };


  /**
   * Install a callback to handle the upload of one file using an
   * "<input type="file" />" element. The ID of the <input> element in
   * the DOM is provided by argument "elementId".
   *
   * Whenever a file is uploaded, the JavaScript will make a POST call
   * to the URI of the back-end specified in the "uri" argument. The
   * "filename" argument sets the name of the file that is read by the
   * back-end.
   * 
   * On success of the upload, "responseCallback(response)" is
   * invoked.  The "response" value is a JavaScript object of type
   * ArrayBuffer, that can be further processed by the functions
   * "LoadImageFromBackendIntoCanvas()" and "ParseJsonFromBackendUpload()".
   *
   * Usage in the LSINC1114 course: You will use this function in
   * most exercises.
   **/
  this.InstallFileUploader = function(elementId, uri, filename, responseCallback) {
    var input = document.getElementById(elementId);
    
    if (input == null) {
      throw 'Unknown input element: ' + elementId;
    } else if (!(input instanceof HTMLInputElement) ||
               input.getAttribute('type') !== 'file') {
      throw 'Element is not a file uploader: ' + elementId;
    } else {    
      input.addEventListener('change', function(event) {
        var file = input.files[0];  // File from input element
        var req = new XMLHttpRequest();

        var formData = new FormData();
        formData.append(filename, file);

        req.onreadystatechange = function() {
          if (req.readyState === 4) {  // DONE - The operation is complete
            if (req.status === 200) {
              if (responseCallback !== null) {
                responseCallback({ data : req.response });
              }
            } else {
              console.error('Error in the back-end: ', req.statusText);
            }
          }
        }

        req.responseType = 'arraybuffer';
        req.open('POST', uri);
        req.send(formData);
      });
    }
  };


  /**
   * Paste the content of an image generated by the back-end, into the
   * canvas given as an argument. This function is to be used in the
   * "responseCallback(response)" installed by the function
   * "InstallFileUploader()".
   **/
  this.LoadImageFromBackendIntoCanvas = function(canvas, response) {
    if (response === null ||
        !(response instanceof ArrayBuffer) ||
        response.length < 8) {
      throw 'Invalid image received from the back-end';
    } else {    
      arr = new Uint8ClampedArray(response, 0, 8);
      width = ((arr[0] * 256 + arr[1]) * 256 + arr[2]) * 256 + arr[3];
      height = ((arr[4] * 256 + arr[5]) * 256 + arr[6]) * 256 + arr[7];

      canvas.width = width;
      canvas.height = height;
        
      if (width != 0 && height != 0) {
        image = new ImageData(new Uint8ClampedArray(response, 8), width, height, {});
        var ctx = canvas.getContext('2d');
        ctx.width = image.width;
        ctx.height = image.height;
        ctx.putImageData(image, 0, 0);
      }
    }
  };


  /**
   * Parse the content of a JSON generated by the back-end. This
   * function is ONLY to be used in the "responseCallback(response)"
   * installed by the function "InstallFileUploader()". It must *not*
   * be used for axios requests.
   **/
  this.ParseJsonFromBackendUpload = function(response) {
    if (response === null ||
        !(response instanceof ArrayBuffer)) {
      throw 'Invalid JSON received from the back-end';
    } else {
      return JSON.parse(new TextDecoder().decode(response));
    }
  };


  /**
   * Given one (x,y) position on the canvas that corresponds to the
   * container whose ID in the DOM is given by "containerId", convert
   * this position to position (x',y') expressed in the coordinate
   * system of the scene.
   **/
  this.MapCanvasToSceneCoordinates = function(containerId, x, y) {
    var container = GetContainerById(containerId);
    var canvas = GetCanvas(container);

    var viewport = new DOMPoint(x - container.offsetWidth / 2.0,
                                y - container.offsetHeight / 2.0);
    var scene = canvas.properties.transform.inverse().transformPoint(viewport);

    return [ scene.x, scene.y ];
  };


  /**
   * Given the ID of a <select> element in the DOM (which corresponds
   * to a dropdown list), clear all the available options for this
   * dropdown.
   **/
  this.ClearSelect = function(selectId) {
    var select = document.getElementById(selectId);

    while (select.options.length > 0) {
      select.options.remove(0);
    }
  };


  /**
   * Given the ID of a <select> element in the DOM (which corresponds
   * to a dropdown list), add a new option to the dropdown with the
   * provided key and value. The "value" is the text that is shown to
   * the user, whereas "key" is an unique value that identifies the
   * option. The key of the selected option can be retrieved using the
   * function "GetSelectedOption()".
   **/
  this.AddSelectOption = function(selectId, key, value) {
    var select = document.getElementById(selectId);
    select.appendChild(new Option(value, key));
  };


  /**
   * Given the ID of a <select> element in the DOM (which corresponds
   * to a dropdown list), return the key that is associated with the
   * currently selected option.
   **/
  this.GetSelectedOption = function(selectId) {
    var select = document.getElementById(selectId);
    if (select.options.length == 0) {
      return null;
    } else {
      return select.options[select.selectedIndex].value;
    }
  };
};


// Whenever the user resizes the browser window, automatically redraw
// all the containers that are managed by the BestRendering library
window.addEventListener('resize', BestRendering.DrawAll, false);

// Once the DOM is ready, automatically fit the viewport of all the
// containers that are managed by the BestRendering library
window.addEventListener('DOMContentLoaded', BestRendering.FitAll, false);
