/*
 * Obtained from: https://github.com/lsiden/export-jqplot-to-png/blob/master/export-jqplot-to-png.js
 * Unsure of the license
 */
$(function() {

  $.fn.jqplotToImage =
  function(x_offset, y_offset) {
    if ($(this).width() == 0 || $(this).height() == 0) {
      return null;
    }
    var newCanvas = document.createElement("canvas");
    newCanvas.width = $(this).outerWidth() + Number(x_offset);
    newCanvas.height = $(this).outerHeight() + Number(y_offset);

    if (!newCanvas.getContext) return null;

    var newContext = newCanvas.getContext("2d"); 
    newContext.textAlign = 'left';
    newContext.textBaseline = 'top';
    

    function _jqpToImage(el, x_offset, y_offset) {
      var tagname = el.tagName.toLowerCase();
      var p = $(el).position();
      var css = getComputedStyle(el);
      var left = x_offset + p.left + parseInt(css.marginLeft) + parseInt(css.borderLeftWidth) + parseInt(css.paddingLeft);
      var top = y_offset + p.top + parseInt(css.marginTop) + parseInt(css.borderTopWidth)+ parseInt(css.paddingTop);

      if ((tagname == 'div' || tagname == 'span') && !$(el).hasClass('jqplot-highlighter-tooltip')) {
        $(el).children().each(function() {
          _jqpToImage(this, left, top);
        });
        var text = $(el).childText();

        if (text) {
          var metrics = newContext.measureText(text);
          newContext.font = $(el).getComputedFontStyle();
          newContext.fillText(text, left, top);
          // For debugging.
          //newContext.strokeRect(left, top, $(el).width(), $(el).height());
        }
      }
      else if (tagname == 'canvas') {
        newContext.drawImage(el, left, top);
      }
    }
    $(this).children().each(function() {
      _jqpToImage(this, x_offset, y_offset);
    });
    return newCanvas;
  };

  $.fn.css2 = jQuery.fn.css;
  $.fn.css = function() {
    if (arguments.length) return jQuery.fn.css2.apply(this, arguments);
    return window.getComputedStyle(this[0]);
  };

  // Returns font style as abbreviation for "font" property.
  $.fn.getComputedFontStyle = function() {
    var css = this.css();
    var attr = ['font-style', 'font-weight', 'font-size', 'font-family'];
    var style = [];

    for (var i=0 ; i < attr.length; ++i) {
      var attr = String(css[attr[i]]);

      if (attr && attr != 'normal') {
        style.push(attr);
      }
    }
    return style.join(' ');
  }

  $.fn.childText =
    function() {
      return $(this).contents().filter(function() {
        return this.nodeType == 3;  // Node.TEXT_NODE not defined in I7
      }).text();
    };

});

/*
 * obtained from https://bitbucket.org/cleonello/jqplot/issue/14/export-capabilities#comment-554274
 */
function getLineheight(obj) {
    var lineheight;
    if (obj.css('line-height') == 'normal') {
        lineheight = obj.css('font-size');
    } else {
        lineheight = obj.css('line-height');
    }
    return parseInt(lineheight.replace('px',''));
}

function getTextAlign(obj) {
    var textalign = obj.css('text-align');
    if (textalign == '-webkit-auto') {
        textalign = 'left';
    }
    return textalign;
}

function printAtWordWrap(context, text, x, y, fitWidth, lineheight) {
    var textArr = [];
    fitWidth = fitWidth || 0;

    if (fitWidth <= 0) {
        textArr.push(text);
    }
    
    var words = text.split(' ');
    var idx = 1;
    while (words.length > 0 && idx <= words.length) {
        var str = words.slice(0, idx).join(' ');
        var w = context.measureText(str).width;
        if (w > fitWidth) {
            if (idx == 1) {
                idx = 2;
            }
            textArr.push(words.slice(0, idx - 1).join(' '));
            words = words.splice(idx - 1);
            idx = 1;
        } else {
            idx++;
        }
    }
    if (words.length && idx > 0) {
        textArr.push(words.join(' '));
    }
    if (context.textAlign == 'center') {
        x += fitWidth/2;
    }
    if (context.textBaseline == 'middle') {
        y -= lineheight/2;
    } else if(context.textBaseline == 'top') {
        y -= lineheight;
    }
    for (idx = textArr.length - 1; idx >= 0; idx--) {
        var line = textArr.pop();
        if (context.measureText(line).width > fitWidth && context.textAlign == 'center') {
            x -= fitWidth/2;
            context.textAlign = 'left';
            context.fillText(line, x, y + (idx+1) * lineheight);
            context.textAlign = 'center';
            x += fitWidth/2;
        } else {
            context.fillText(line, x, y + (idx+1) * lineheight);
        }
    }
}

function findPlotSize(obj) {
    var width = obj.width();
    var height = obj.height();
    var legend = obj.find('.jqplot-table-legend');
    if (legend.position()) {
        height = legend.position().top + legend.height();
    }
    obj.find('*').each(function() {
        var offset = $(this).offset();
        tempWidth = offset.left + $(this).width()
        tempHeight = $(this).height()
        if(tempWidth > width) {width = tempWidth;}
        if(tempHeight > height) {height = tempHeight;}
    });
    return {width: width, height: height};
}

function jqplotToImg(obj) {
    var newCanvas = document.createElement("canvas");
    var size = findPlotSize(obj);
    newCanvas.width = size.width;
    newCanvas.height = size.height;
    
    // check for plot error
    var baseOffset = obj.offset();
    if (obj.find("canvas.jqplot-base-canvas").length) {
        baseOffset = obj.find("canvas.jqplot-base-canvas").offset();
        baseOffset.left -= parseInt(obj.css('margin-left').replace('px', ''));
    }

    // fix background color for pasting
    var context = newCanvas.getContext("2d");
    var backgroundColor = "rgba(255,255,255,1)";
    obj.children(':first-child').parents().each(function () {
        if ($(this).css('background-color') != 'transparent') {
            backgroundColor = $(this).css('background-color');
            return false;
        }
    });
    context.fillStyle = backgroundColor;
    context.fillRect(0, 0, newCanvas.width, newCanvas.height);
    
    // add main plot area
    obj.find('canvas').each(function () {
        var offset = $(this).offset();
        newCanvas.getContext("2d").drawImage(this,
            offset.left - baseOffset.left,
            offset.top - baseOffset.top
        );
    });
    
    obj.find(".jqplot-series-canvas > div").each(function() {
        var offset = $(this).offset();
        var context = newCanvas.getContext("2d");
        context.fillStyle = $(this).css('background-color');
        context.fillRect(
            offset.left - baseOffset.left - parseInt($(this).css('padding-left').replace('px', '')),
            offset.top - baseOffset.top,
            $(this).width() + parseInt($(this).css('padding-left').replace('px', '')) + parseInt($(this).css('padding-right').replace('px', '')),
            $(this).height() + parseInt($(this).css('padding-top').replace('px', '')) + parseInt($(this).css('padding-bottom').replace('px', ''))
        );
        context.font = [$(this).css('font-style'), $(this).css('font-size'), $(this).css('font-family')].join(' ');
        context.fillStyle = $(this).css('color');
        context.textAlign = getTextAlign($(this));
        var txt = $.trim($(this).html()).replace(/<br style="">/g, ' ');
        var lineheight = getLineheight($(this));
        printAtWordWrap(context, txt, offset.left-baseOffset.left, offset.top - baseOffset.top - parseInt($(this).css('padding-top').replace('px', '')), $(this).width(), lineheight);
    });
    
    // add x-axis labels, y-axis labels, point labels
    obj.find('div.jqplot-axis > div, div.jqplot-point-label, div.jqplot-error-message, .jqplot-data-label, div.jqplot-meterGauge-tick, div.jqplot-meterGauge-label').each(function() {
        var offset = $(this).offset();
        var context = newCanvas.getContext("2d");
        context.font = [$(this).css('font-style'), $(this).css('font-size'), $(this).css('font-family')].join(' ');
        context.fillStyle = $(this).css('color');
        var txt = $.trim($(this).text());
        var lineheight = getLineheight($(this));
        printAtWordWrap(context, txt, offset.left-baseOffset.left, offset.top - baseOffset.top - 2.5, $(this).width(), lineheight);
    });
    
    // add the title
    obj.children("div.jqplot-title").each(function() {
        var offset = $(this).offset();
        var context = newCanvas.getContext("2d");
        context.font = [$(this).css('font-style'), $(this).css('font-size'), $(this).css('font-family')].join(' ');
        context.textAlign = getTextAlign($(this));
        context.fillStyle = $(this).css('color');
        var txt = $.trim($(this).text());
        var lineheight = getLineheight($(this));
        printAtWordWrap(context, txt, offset.left-baseOffset.left, offset.top - baseOffset.top, newCanvas.width - parseInt(obj.css('margin-left').replace('px', ''))- parseInt(obj.css('margin-right').replace('px', '')), lineheight);
    });
    
    // add the legend
    obj.children("table.jqplot-table-legend").each(function() {
        var offset = $(this).offset();
        var context = newCanvas.getContext("2d");
        context.strokeStyle = $(this).css('border-top-color');
        context.strokeRect(
            offset.left - baseOffset.left,
            offset.top - baseOffset.top,
            $(this).width(),$(this).height()
        );
        context.fillStyle = $(this).css('background-color');
        context.fillRect(
            offset.left - baseOffset.left,
            offset.top - baseOffset.top,
            $(this).width(),$(this).height()
        );
    });
    
    // add the swatches
    obj.find("div.jqplot-table-legend-swatch").each(function() {
        var offset = $(this).offset();
        var context = newCanvas.getContext("2d");
        context.fillStyle = $(this).css('border-top-color');
        context.fillRect(
            offset.left - baseOffset.left,
            offset.top - baseOffset.top,
            $(this).parent().width(),$(this).parent().height()
        );
    });
        
    obj.find("td.jqplot-table-legend").each(function() {
        var offset = $(this).offset();
        var context = newCanvas.getContext("2d");
        context.font = [$(this).css('font-style'), $(this).css('font-size'), $(this).css('font-family')].join(' ');
        context.fillStyle = $(this).css('color');
        context.textAlign = getTextAlign($(this));
        context.textBaseline = $(this).css('vertical-align');
        var txt = $.trim($(this).text());
        var lineheight = getLineheight($(this));
        printAtWordWrap(context, txt, offset.left-baseOffset.left, offset.top - baseOffset.top + parseInt($(this).css('padding-top').replace('px','')), $(this).width(), lineheight);
    });

    // then convert the image to base64 format
    return newCanvas;
}